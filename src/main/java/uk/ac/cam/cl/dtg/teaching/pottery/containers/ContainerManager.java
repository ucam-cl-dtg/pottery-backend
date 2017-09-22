/*
 * pottery-backend - Backend API for testing programming exercises
 * Copyright © 2015 Andrew Rice (acr31@cam.ac.uk)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package uk.ac.cam.cl.dtg.teaching.pottery.containers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.cam.cl.dtg.teaching.docker.ApiListener;
import uk.ac.cam.cl.dtg.teaching.docker.ApiUnavailableException;
import uk.ac.cam.cl.dtg.teaching.docker.Docker;
import uk.ac.cam.cl.dtg.teaching.docker.DockerPatch;
import uk.ac.cam.cl.dtg.teaching.docker.DockerUtil;
import uk.ac.cam.cl.dtg.teaching.docker.api.DockerApi;
import uk.ac.cam.cl.dtg.teaching.docker.model.Container;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerConfig;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerHostConfig;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerInfo;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerResponse;
import uk.ac.cam.cl.dtg.teaching.docker.model.SystemInfo;
import uk.ac.cam.cl.dtg.teaching.docker.model.Version;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.Stoppable;
import uk.ac.cam.cl.dtg.teaching.pottery.config.ContainerEnvConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.ContainerExecutionException;
import uk.ac.cam.cl.dtg.teaching.pottery.model.ContainerRestrictions;
import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.HarnessResponse;
import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.Measurement;
import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.ValidatorResponse;

@Singleton
public class ContainerManager implements Stoppable {

  protected static final Logger LOG = LoggerFactory.getLogger(ContainerManager.class);

  private ContainerEnvConfig config;

  // Lazy initialized - use getDockerApi to access this
  private DockerApi dockerApi;

  private String apiStatus = "UNITITIALISED";

  private ScheduledExecutorService scheduler;

  private ConcurrentSkipListSet<String> runningContainers = new ConcurrentSkipListSet<>();

  private long smoothedCallTime = 0;
  private AtomicInteger counter = new AtomicInteger(0);
  private AtomicInteger timeoutMultiplier = new AtomicInteger(1);

  @Inject
  public ContainerManager(ContainerEnvConfig config) throws IOException, ApiUnavailableException {
    this.config = config;
    this.scheduler = Executors.newSingleThreadScheduledExecutor();
    FileUtil.mkdir(config.getLibRoot());
  }

  public synchronized String getApiStatus() {
    return apiStatus;
  }

  public synchronized long getSmoothedCallTime() {
    return smoothedCallTime;
  }

  private synchronized DockerApi getDockerApi() throws ApiUnavailableException {
    if (dockerApi == null) {
      DockerApi docker =
          new Docker("localhost", 2375, 1)
              .api(
                  new ApiListener() {
                    @Override
                    public void callCompleted(
                        boolean apiAvailable, long timeTaken, String methodName) {
                      synchronized (ContainerManager.this) {
                        smoothedCallTime =
                            (timeTaken >> 3) + smoothedCallTime - (smoothedCallTime >> 3);
                        if (!apiAvailable) {
                          apiStatus = "FAILED";
                        } else if (smoothedCallTime > 1000) {
                          apiStatus = "SLOW_RESPONSE_TIME";
                        } else {
                          apiStatus = "OK";
                        }
                      }
                    }
                  });
      if (LOG.isInfoEnabled()) {
        Version v = docker.getVersion();
        LOG.info("Connected to docker, API version: {}", v.getApiVersion());
      }
      SystemInfo info = docker.systemInfo();
      if (info.getSwapLimit() == null || !info.getSwapLimit().booleanValue()) {
        LOG.warn(
            "WARNING: swap limits are disabled for this kernel. Add \"cgroup_enable=memory "
                + "swapaccount=1\" to your kernel command line");
      }

      for (Container i : docker.listContainers(true, null, null, null, null)) {
        String matchedName = getPotteryTransientName(i);
        if (matchedName != null) {
          LOG.warn("Deleting old container named {}", matchedName);
          try {
            docker.deleteContainer(i.getId(), true, true);
          } catch (RuntimeException e) {
            LOG.error("Error deleting old container", e);
          }
        }
      }
      dockerApi = docker;
      apiStatus = "OK";
    }
    return dockerApi;
  }

  private String getPotteryTransientName(Container i) {
    final String prefix = "/" + config.getContainerPrefix();
    for (String name : i.getNames()) {
      if (name.startsWith(prefix)) {
        return name;
      }
    }
    return null;
  }

  @Override
  public void stop() {
    LOG.info("Shutting down scheduler");
    for (Runnable r : scheduler.shutdownNow()) {
      r.run();
    }
    LOG.info("Killing remaining containers");
    try {
      DockerApi docker = getDockerApi();
      for (String containerId : runningContainers) {
        DockerUtil.killContainer(containerId, docker);
      }
      docker.close();
    } catch (ApiUnavailableException e) {
      LOG.error("Unable to remove running containers, API unavailable", e);
    }
  }

  public <T> ContainerExecResponse<T> exec_container(
      ContainerManager.PathPair[] mapping,
      String[] command,
      String imageName,
      String stdin,
      ContainerRestrictions restrictions,
      Function<String, T> converter)
      throws ContainerExecutionException, ApiUnavailableException {

    String containerName = this.config.getContainerPrefix() + counter.incrementAndGet();
    LOG.debug("Creating container {}", containerName);

    DockerApi docker = getDockerApi();

    long startTime = System.currentTimeMillis();
    try {
      ContainerConfig config = new ContainerConfig();
      config.setOpenStdin(true);
      config.setEnv(Arrays.asList("LOCAL_USER_ID=" + this.config.getUid()));
      config.setCmd(Arrays.asList(command));
      config.setImage(imageName);
      config.setNetworkDisabled(restrictions.isNetworkDisabled());
      ContainerHostConfig hc = new ContainerHostConfig();
      hc.setMemory(restrictions.getRamLimitMegabytes() * 1024 * 1024);
      hc.setMemorySwap(hc.getMemory()); // disable swap
      String[] binds = new String[mapping.length];
      for (int i = 0; i < mapping.length; ++i) {
        LOG.debug(
            "Added mapping {} -> {} readWrite:{}",
            mapping[i].getHost().getPath(),
            mapping[i].getContainer().getPath(),
            mapping[i].isReadWrite());
        binds[i] =
            DockerUtil.bind(
                mapping[i].getHost(), mapping[i].getContainer(), !mapping[i].isReadWrite());
      }
      hc.setBinds(Arrays.asList(binds));
      config.setHostConfig(hc);
      Map<String, Map<String, String>> volumes = new HashMap<String, Map<String, String>>();
      for (ContainerManager.PathPair p : mapping) {
        volumes.put(p.getContainer().getPath(), new HashMap<String, String>());
      }
      config.setVolumes(volumes);
      ContainerResponse response = docker.createContainer(containerName, config);
      final String containerId = response.getId();
      runningContainers.add(containerId);
      try {
        docker.startContainer(containerId);
        StringBuffer output = new StringBuffer();
        AttachListener l = new AttachListener(output, stdin);

        ScheduledFuture<Boolean> timeoutKiller = null;
        if (restrictions.getTimeoutSec() > 0) {
          timeoutKiller =
              scheduler.schedule(
                  new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                      try {
                        DockerUtil.killContainer(containerId, docker);
                        l.notifyClose();
                        return true;
                      } catch (RuntimeException e) {
                        LOG.error("Caught exception killing container", e);
                        return false;
                      }
                    }
                  },
                  restrictions.getTimeoutSec() * timeoutMultiplier.get(),
                  TimeUnit.SECONDS);
        }

        DiskUsageKiller diskUsageKiller =
            new DiskUsageKiller(
                containerId, docker, restrictions.getDiskWriteLimitMegabytes() * 1024 * 1024, l);
        ScheduledFuture<?> diskUsageKillerFuture =
            scheduler.scheduleAtFixedRate(diskUsageKiller, 10L, 10L, TimeUnit.SECONDS);
        try {
          Future<Session> session = docker.attach(containerId, true, true, true, true, true, l);

          // Wait for container to finish (or be killed)
          boolean success = false;
          boolean knownStopped = false;
          boolean closed = false;

          while (!closed) {
            try {
              closed = l.waitForClose(60 * 1000);
            } catch (InterruptedException e) {
              // ignore
            }
            if (!closed) {
              ContainerInfo i = docker.inspectContainer(containerId, false);
              if (!i.getState().getRunning()) {
                knownStopped = true;
                closed = true;
                success = i.getState().getExitCode() == 0;
              }
            }
          }

          // Check to make sure its really stopped the container
          if (!knownStopped) {
            ContainerInfo i = docker.inspectContainer(containerId, false);
            if (i.getState().getRunning()) {
              DockerUtil.killContainer(containerId, docker);
              i = docker.inspectContainer(containerId, false);
            }
            success = i.getState().getExitCode() == 0;
          }

          if (timeoutKiller != null) {
            timeoutKiller.cancel(false);
            try {
              if (timeoutKiller.get()) {
                throw new ContainerExecutionException(
                    "Timed out after " + restrictions.getTimeoutSec() + " seconds");
              }
            } catch (InterruptedException | ExecutionException | CancellationException e) {
              // ignore
            }
          }
          if (diskUsageKiller.isKilled()) {
            throw new ContainerExecutionException(
                "Excessive disk usage. Recorded "
                    + diskUsageKiller.getBytesWritten()
                    + " bytes written. Limit is "
                    + restrictions.getDiskWriteLimitMegabytes() * 1024 * 1024);
          }

          try {
            session.get().close();
          } catch (InterruptedException e) {
            // ignore
          } catch (ExecutionException e) {
            LOG.error(
                "An exception occurred collecting the websocket session from the future",
                e.getCause());
          }

          LOG.debug("Container response: {}", output.toString());
          return new ContainerExecResponse<>(
              success,
              converter.apply(output.toString()),
              output.toString(),
              System.currentTimeMillis() - startTime);
        } finally {
          diskUsageKillerFuture.cancel(false);
        }
      } finally {
        runningContainers.remove(containerId);
        DockerPatch.deleteContainer(docker, containerId, true, true);
      }
    } catch (RuntimeException e) {
      LOG.error("Error executing container", e);
      throw new ContainerExecutionException(
          "An error ("
              + e.getClass().getName()
              + ") occurred when executing container: "
              + e.getMessage());
    }
  }

  public ContainerExecResponse<String> execTaskCompilation(
      File taskDirHost, String imageName, ContainerRestrictions restrictions)
      throws ApiUnavailableException {
    try {
      return exec_container(
          new PathPair[] {new PathPair(taskDirHost, "/task", true)},
          new String[] {"/task/compile-test.sh", "/task/test", "/task/harness", "/task/validator"},
          imageName,
          null,
          restrictions,
          Function.identity());
    } catch (ContainerExecutionException e) {
      return new ContainerExecResponse<>(false, e.getMessage(), e.getMessage(), -1);
    }
  }

  public ContainerExecResponse<String> execCompilation(
      File codeDirHost,
      File compilationRecipeDirHost,
      String imageName,
      ContainerRestrictions restrictions)
      throws ApiUnavailableException {
    try {
      return exec_container(
          new PathPair[] {
            new PathPair(codeDirHost, "/code", true),
            new PathPair(compilationRecipeDirHost, "/compile", false),
            new PathPair(config.getLibRoot(), "/testlib", false)
          },
          new String[] {"/compile/compile-solution.sh", "/code", "/testlib"},
          imageName,
          null,
          restrictions,
          Function.identity());
    } catch (ContainerExecutionException e) {
      return new ContainerExecResponse<>(false, e.getMessage(), e.getMessage(), -1);
    }
  }

  public ContainerExecResponse<HarnessResponse> execHarness(
      File codeDirHost,
      File harnessRecipeDirHost,
      String imageName,
      ContainerRestrictions restrictions)
      throws ApiUnavailableException {
    try {
      return exec_container(
          new PathPair[] {
            new PathPair(codeDirHost, "/code", true),
            new PathPair(harnessRecipeDirHost, "/harness", false),
            new PathPair(config.getLibRoot(), "/testlib", false)
          },
          new String[] {"/harness/run-harness.sh", "/code", "/harness", "/testlib"},
          imageName,
          null,
          restrictions,
          new Function<String, HarnessResponse>() {
            @Override
            public HarnessResponse apply(String t) {
              try {
                ObjectMapper o = new ObjectMapper();
                return o.readValue(t, HarnessResponse.class);
              } catch (IOException e) {
                return new HarnessResponse(
                    "Failed to deserialise response from harness: "
                        + e.getMessage()
                        + ". Response was "
                        + t);
              }
            }
          });
    } catch (ContainerExecutionException e) {
      return new ContainerExecResponse<>(
          false, new HarnessResponse(e.getMessage()), e.getMessage(), -1);
    }
  }

  public ContainerExecResponse<ValidatorResponse> execValidator(
      File validatorDirectory,
      HarnessResponse harnessResponse,
      String imageName,
      ContainerRestrictions restrictions)
      throws ApiUnavailableException {
    ObjectMapper o = new ObjectMapper();
    List<Measurement> m =
        harnessResponse
            .getTestParts()
            .stream()
            .map(p -> p.getMeasurements())
            .collect(ArrayList::new, ArrayList::addAll, ArrayList::addAll);
    String stdin;
    try {
      stdin = o.writeValueAsString(m);
    } catch (JsonProcessingException e) {
      return new ContainerExecResponse<>(
          false,
          new ValidatorResponse("Failed to serialise measurement list"),
          "Failed to serialise measurement list",
          -1);
    }

    try {
      return exec_container(
          new PathPair[] {
            new PathPair(validatorDirectory, "/validator", false),
            new PathPair(config.getLibRoot(), "/testlib", false)
          },
          new String[] {"/validator/run-validator.sh", "/validator", "/testlib"},
          imageName,
          stdin,
          restrictions,
          new Function<String, ValidatorResponse>() {
            @Override
            public ValidatorResponse apply(String t) {
              try {
                ObjectMapper o = new ObjectMapper();
                return o.readValue(t, ValidatorResponse.class);
              } catch (IOException e) {
                return new ValidatorResponse(
                    "Failed to deserialise response from validator: "
                        + e.getMessage()
                        + ". Response was "
                        + t);
              }
            }
          });
    } catch (ContainerExecutionException e) {
      return new ContainerExecResponse<>(
          false, new ValidatorResponse(e.getMessage()), e.getMessage(), -1);
    }
  }

  public void setTimeoutMultiplier(int multiplier) {
    timeoutMultiplier.set(multiplier);
  }

  static class PathPair {
    private File host;
    private File container;
    private boolean readWrite;

    public PathPair(File host, String container, boolean readWrite) {
      super();
      this.host = host;
      this.container = new File(container);
      this.readWrite = readWrite;
    }

    public File getHost() {
      return host;
    }

    public File getContainer() {
      return container;
    }

    public boolean isReadWrite() {
      return readWrite;
    }
  }
}
