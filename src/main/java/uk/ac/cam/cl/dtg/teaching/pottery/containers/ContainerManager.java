/**
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import uk.ac.cam.cl.dtg.teaching.docker.Docker;
import uk.ac.cam.cl.dtg.teaching.docker.DockerUtil;
import uk.ac.cam.cl.dtg.teaching.docker.api.DockerApi;
import uk.ac.cam.cl.dtg.teaching.docker.model.Container;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerConfig;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerHostConfig;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerResponse;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerStartConfig;
import uk.ac.cam.cl.dtg.teaching.docker.model.SystemInfo;
import uk.ac.cam.cl.dtg.teaching.docker.model.Version;
import uk.ac.cam.cl.dtg.teaching.docker.model.WaitResponse;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.Stoppable;
import uk.ac.cam.cl.dtg.teaching.pottery.config.ContainerEnvConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.ContainerExecutionException;
import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.HarnessResponse;
import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.Measurement;
import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.ValidatorResponse;

@Singleton
public class ContainerManager implements Stoppable {

	protected static final Logger LOG = LoggerFactory.getLogger(ContainerManager.class);
	
	private ContainerEnvConfig config;

	private DockerApi docker;
	
	private ScheduledExecutorService scheduler;
	
	private ConcurrentSkipListSet<String> runningContainers = new ConcurrentSkipListSet<>();
	
	@Inject
	public ContainerManager(ContainerEnvConfig config) throws IOException {
		this.config = config;
		this.docker = new Docker("localhost",2375).api();
		this.scheduler = Executors.newSingleThreadScheduledExecutor();
		FileUtil.mkdir(config.getLibRoot());
		
		if (LOG.isInfoEnabled()) {
			Version v = docker.getVersion();		
			LOG.info("Connected to docker, API version: {}",v.getApiVersion());
		}
		
		SystemInfo info = docker.systemInfo();
		if (info.getSwapLimit() == null || !info.getSwapLimit().booleanValue()) {
			LOG.warn("WARNING: swap limits are disabled for this kernel. Add \"cgroup_enable=memory swapaccount=1\" to your kernel command line");
		}
		
		for(Container i : docker.listContainers(true, null, null, null, null)) {
			String matchedName = getPotteryTransientName(i);
			if (matchedName != null) {
				LOG.warn("Deleting old container named {}",matchedName);
				try {
					docker.deleteContainer(i.getId(), true, true);
				} catch (RuntimeException e) {
					LOG.error("Error deleting old container",e);
				}
			}
		}
	}
	
	private String getPotteryTransientName(Container i) {
		final String prefix = "/"+config.getContainerPrefix();
		for (String name : i.getNames()) {
			if (name.startsWith(prefix)) { return name; }
		}
		return null;
	}
	
	@Override
	public void stop() {
		LOG.info("Shutting down scheduler");
		for(Runnable r : scheduler.shutdownNow()) {
			r.run();
		}
		LOG.info("Killing remaining containers");
		for(String containerId : runningContainers) {
			DockerUtil.killContainer(containerId, docker);
		}
		docker.close();
	}
	
	static class PathPair {
		private File host;
		private File container;
		public PathPair(File host, String container) {
			super();
			this.host = host;
			this.container = new File(container);
		}
		public File getHost() {
			return host;
		}
		public File getContainer() {
			return container;
		}
	}

	private AtomicInteger counter = new AtomicInteger(0);

	public <T> ContainerExecResponse<T> exec_container(ContainerManager.PathPair[] mapping, String command, String imageName, String stdin, ContainerRestrictions restrictions, Function<String,T> converter) throws ContainerExecutionException {

		String containerName = this.config.getContainerPrefix()+counter.incrementAndGet();
		LOG.debug("Creating container {}",containerName);
			
		try {
			DockerUtil.deleteContainerByName(containerName,docker);
		} catch (RuntimeException e) {
			throw new ContainerExecutionException(e.getMessage());
		}	
		
		long startTime = System.currentTimeMillis();
		try {
			ContainerConfig config = new ContainerConfig();
			config.setOpenStdin(true);
			config.setCmd(Arrays.asList("/usr/bin/sudo","-u","#"+this.config.getUid(),"/bin/bash","-c",command));
			config.setImage(imageName);
			ContainerHostConfig hc = new ContainerHostConfig();			
			hc.setMemory(restrictions.getRamLimitMegabytes() * 1024 * 1024);
			hc.setMemorySwap(hc.getMemory()); // disable swap
			config.setHostConfig(hc);
			Map<String,Map<String,String>> volumes = new HashMap<String,Map<String,String>>();
			for(ContainerManager.PathPair p : mapping) {
				volumes.put(p.getContainer().getPath(), new HashMap<String,String>());
			}
			config.setVolumes(volumes);			
			ContainerResponse response = docker.createContainer(containerName,config);		
			final String containerId = response.getId();
			runningContainers.add(containerId);
			try {				
				ContainerStartConfig startConfig = new ContainerStartConfig();
				String[] binds = new String[mapping.length];
				for(int i=0;i<mapping.length;++i) {
					LOG.debug("Added mapping {} -> {}",mapping[i].getHost().getPath(),mapping[i].getContainer().getPath());
					binds[i] = DockerUtil.bind(mapping[i].getHost(),mapping[i].getContainer());
				}
				startConfig.setBinds(binds);
				docker.startContainer(containerId,startConfig);
				
				ScheduledFuture<Boolean> timeoutKiller = null;
				if (restrictions.getTimeoutSec() > 0) {
					timeoutKiller = scheduler.schedule(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							try {
								return DockerUtil.killContainer(containerId,docker);
							} catch (RuntimeException e) {
								LOG.error("Caught exception killing container",e);
								return false;
							}
						}					
					}, restrictions.getTimeoutSec(),TimeUnit.SECONDS);
				}
				
				DiskUsageKiller diskUsageKiller = new DiskUsageKiller(containerId,docker,restrictions.getDiskWriteLimitMegabytes() * 1024 * 1024);
				ScheduledFuture<?> diskUsageKillerFuture = scheduler.scheduleAtFixedRate(diskUsageKiller,10L, 10L, TimeUnit.SECONDS);
				try {
					StringBuffer output = new StringBuffer();
					AttachListener l = new AttachListener(output,stdin);
					Future<Session> session = docker.attach(containerId,true,true,true,true,true,l);
					
					// Wait for container to finish (or be killed)
					WaitResponse waitResponse = docker.waitContainer(containerId);
				
					if (timeoutKiller != null) {
						boolean killed = false;				
						if (!timeoutKiller.isDone()) {
							timeoutKiller.cancel(false);
						}
						else {
							try {
								killed = timeoutKiller.get();
							} catch (InterruptedException|ExecutionException e) {}
						}
						if (killed) {
							throw new ContainerExecutionException("Timed out after "+restrictions.getTimeoutSec()+" seconds");
						}	
					}
					if (diskUsageKiller.isKilled()) {
						throw new ContainerExecutionException("Excessive disk usage. Recorded "+diskUsageKiller.getBytesWritten()+" bytes written");
					}
					
					try {
						session.get().disconnect();
					} catch (IOException e) {
						LOG.error("Failed to disconnect from websocket session with container "+containerId,e);
					} catch (InterruptedException e) {
					} catch (ExecutionException e) {
						LOG.error("An exception occurred collecting the websocket session from the future",e.getCause());
					}
					
					boolean success = waitResponse.statusCode == 0;
					LOG.debug("Container response: {}",output.toString());
					return new ContainerExecResponse<>(success, converter.apply(output.toString()),System.currentTimeMillis()-startTime);
				}
				finally {
					diskUsageKillerFuture.cancel(false);
				}
			}
			
			finally {
				runningContainers.remove(containerId);
				docker.deleteContainer(containerId, true, true);
			}
		} catch (RuntimeException e) {
			LOG.debug("Error executing container",e);
			throw new ContainerExecutionException(e.getMessage());
		}
	}

	public ContainerExecResponse<String> execTaskCompilation(File taskDirHost, String imageName, ContainerRestrictions restrictions) {
		try {
			return exec_container(new PathPair[] {
					new PathPair(taskDirHost,"/task")
					}, 
					"/task/compile-test.sh /task/test /task/harness /task/validator", 
					imageName, 
					null,
					restrictions,
					Function.identity());
		} catch (ContainerExecutionException e) {
			return new ContainerExecResponse<>(false,e.getMessage(),-1);
		}
	}
	
	public ContainerExecResponse<String> execCompilation(File codeDirHost, File compilationRecipeDirHost, String imageName, ContainerRestrictions restrictions) {
		try {
			return exec_container(new PathPair[] { 
					new PathPair(codeDirHost,"/code"),
					new PathPair(compilationRecipeDirHost,"/compile"),
					new PathPair(config.getLibRoot(),"/testlib") },
				"/compile/compile-solution.sh /code /testlib",
				imageName,
				null, 
				restrictions,
				Function.identity());
		} catch (ContainerExecutionException e) {
			return new ContainerExecResponse<>(false,e.getMessage(),-1);
		}			
	}

	public ContainerExecResponse<HarnessResponse> execHarness(File codeDirHost, File harnessRecipeDirHost, String imageName, ContainerRestrictions restrictions) {
		try {
			return exec_container(new PathPair[] { 
					new PathPair(codeDirHost,"/code"),
					new PathPair(harnessRecipeDirHost,"/harness"),
					new PathPair(config.getLibRoot(),"/testlib") },
				"/harness/run-harness.sh /code /harness /testlib",
				imageName,
				null, 
				restrictions,
				new Function<String,HarnessResponse>() {
					@Override
						public HarnessResponse apply(String t) {
						try {
							ObjectMapper o = new ObjectMapper();
							return o.readValue(t, HarnessResponse.class);
						} catch (IOException e) {
							return new HarnessResponse("Failed to deserialise response from harness: "+e.getMessage()+". Response was "+t);
						}
					}
				});
		} catch (ContainerExecutionException e) {
			return new ContainerExecResponse<>(false,new HarnessResponse(e.getMessage()),-1);
		}
	}

	public ContainerExecResponse<ValidatorResponse> execValidator(File validatorDirectory, HarnessResponse harnessResponse,
			String imageName, ContainerRestrictions restrictions) {
		ObjectMapper o = new ObjectMapper();
		List<Measurement> m = harnessResponse.getTestParts().stream().
				map(p -> p.getMeasurements()).
				collect(ArrayList::new, ArrayList::addAll, ArrayList::addAll);
		String stdin;
		try {
			stdin = o.writeValueAsString(m);
		} catch (JsonProcessingException e) {
			return new ContainerExecResponse<>(false,new ValidatorResponse("Failed to serialise measurement list"),-1);
		}

		
		try {
			return exec_container(new PathPair[] { 
					new PathPair(validatorDirectory,"/validator"),
					new PathPair(config.getLibRoot(),"/testlib") },
				"/validator/run-validator.sh /validator /testlib",
				imageName,
				stdin,
				restrictions,
				new Function<String,ValidatorResponse>() {
					@Override
					public ValidatorResponse apply(String t) {
						try {
							ObjectMapper o = new ObjectMapper();
							return o.readValue(t, ValidatorResponse.class);
						} catch (IOException e) {
							return new ValidatorResponse("Failed to deserialise response from validator: "+e.getMessage()+". Response was "+t);
						}							
					}
				});
		} catch (ContainerExecutionException e) {
			return new ContainerExecResponse<>(false,new ValidatorResponse(e.getMessage()),-1);
		}
	}

	

}
