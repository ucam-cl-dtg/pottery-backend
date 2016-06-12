package uk.ac.cam.cl.dtg.teaching.pottery.containers;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import uk.ac.cam.cl.dtg.teaching.docker.DockerUtil;
import uk.ac.cam.cl.dtg.teaching.docker.api.DockerApi;
import uk.ac.cam.cl.dtg.teaching.docker.model.Container;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerConfig;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerResponse;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerStartConfig;
import uk.ac.cam.cl.dtg.teaching.docker.model.Version;
import uk.ac.cam.cl.dtg.teaching.docker.model.WaitResponse;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.Stoppable;
import uk.ac.cam.cl.dtg.teaching.pottery.config.ContainerEnvConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.ContainerKilledException;
import uk.ac.cam.cl.dtg.teaching.programmingtest.java.dto.CompilationResponse;
import uk.ac.cam.cl.dtg.teaching.programmingtest.java.dto.HarnessResponse;
import uk.ac.cam.cl.dtg.teaching.programmingtest.java.dto.ValidationResponse;

@Singleton
public class ContainerManager implements Stoppable {

	public static final Logger LOG = LoggerFactory.getLogger(ContainerManager.class);
	
	private ContainerEnvConfig config;

	private DockerApi docker;
	
	private ScheduledExecutorService scheduler;
	
	@Inject
	public ContainerManager(ContainerEnvConfig config, DockerApi docker) throws IOException {
		this.config = config;
		this.docker = docker;
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

	private static class AttachListener implements WebSocketListener {
		private StringBuffer output;
		private String data;
		
		public AttachListener(StringBuffer output, String data) {
			this.output = output;
			this.data = data;
		}

		@Override
		public void onWebSocketClose(int statusCode, String reason) {			
		}

		@Override
		public void onWebSocketConnect(Session session) {
			if (data != null) {
				try {
					session.getRemote().sendString(data);
				} catch (IOException e) {
					throw new RuntimeException("Failed to send input data to container",e);
				}
			}
		}

		@Override
		public void onWebSocketError(Throwable cause) {
			throw new RuntimeException("WebSocket error attaching to container",cause);
		}

		@Override
		public void onWebSocketBinary(byte[] payload, int offset, int len) {
			throw new RuntimeException("Unexpected binary data from container");
		}

		@Override
		public void onWebSocketText(String message) {
			output.append(message);
		}		
	}
	
	public ExecResponse exec_container(ContainerManager.PathPair[] mapping, String command, String imageName, String stdin, int timeoutSec) throws ContainerKilledException {

		String containerName = this.config.getContainerPrefix()+counter.incrementAndGet();
		LOG.debug("Creating container {}",containerName);
			
		try {
			DockerUtil.deleteContainerByName(containerName,docker);
		} catch (RuntimeException e) {
			return new ExecResponse(false,e.getMessage(),-1);
		}	
		
		long startTime = System.currentTimeMillis();
		try {
			ContainerConfig config = new ContainerConfig();
			config.setOpenStdin(true);
			config.setCmd(Arrays.asList("/usr/bin/sudo","-u","#"+this.config.getUid(),"/bin/bash","-c",command));
			config.setImage(imageName);
			Map<String,Map<String,String>> volumes = new HashMap<String,Map<String,String>>();
			for(ContainerManager.PathPair p : mapping) {
				volumes.put(p.getContainer().getPath(), new HashMap<String,String>());
			}
			config.setVolumes(volumes);			
			ContainerResponse response = docker.createContainer(containerName,config);			
			try {
				final String containerId = response.getId();
				
				ContainerStartConfig startConfig = new ContainerStartConfig();
				String[] binds = new String[mapping.length];
				for(int i=0;i<mapping.length;++i) {
					LOG.debug("Added mapping {} -> {}",mapping[i].getHost().getPath(),mapping[i].getContainer().getPath());
					binds[i] = DockerUtil.bind(mapping[i].getHost(),mapping[i].getContainer());
				}
				startConfig.setBinds(binds);
				docker.startContainer(containerId,startConfig);
				
				ScheduledFuture<Boolean> timeoutKiller = null;
				if (timeoutSec > 0) {
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
					}, timeoutSec,TimeUnit.SECONDS);
				}
				
				DiskUsageKiller diskUsageKiller = new DiskUsageKiller(containerId,docker,this.config.getDiskWriteLimitBytes());
				ScheduledFuture<?> diskUsageKillerFuture = scheduler.scheduleAtFixedRate(diskUsageKiller,10L, 10L, TimeUnit.SECONDS);
				try {
					StringBuffer output = new StringBuffer();
					AttachListener l = new AttachListener(output,stdin);
					docker.attach(containerId,true,true,true,true,true,l);
					
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
							throw new ContainerKilledException("Timed out after "+timeoutSec+" seconds", System.currentTimeMillis() - startTime);
						}	
					}
					if (diskUsageKiller.isKilled()) {
						throw new ContainerKilledException("Excessive disk usage. Recorded "+diskUsageKiller.getBytesWritten()+" bytes written", System.currentTimeMillis() - startTime);
					}
					boolean success = waitResponse.statusCode == 0;
					return new ExecResponse(success, output.toString(),System.currentTimeMillis()-startTime);
				}
				finally {
					diskUsageKillerFuture.cancel(false);
				}
			}
			finally {
				docker.deleteContainer(response.getId(), true, true);
			}
		} catch (RuntimeException e) {
			LOG.error("Error executing container",e);
			return new ExecResponse(false,e.getMessage(),System.currentTimeMillis() - startTime);
		}
	}

	public ExecResponse execTaskCompilation(File taskDirHost, String imageName) {
		try {
			ExecResponse r = exec_container(new PathPair[] {
					new PathPair(taskDirHost,"/task")
			}, "/task/compile-test.sh /task/test /task/harness /task/validator", imageName, null, -1);
			return r;
		} catch (ContainerKilledException e) {
			return new ExecResponse(false,e.getMessage(),e.getExecutionTimeMs());
		}
	}
	
	public CompilationResponse execCompilation(File codeDirHost, File compilationRecipeDirHost, String imageName) {
		try {
			ExecResponse r = exec_container(new PathPair[] { 
					new PathPair(codeDirHost,"/code"),
					new PathPair(compilationRecipeDirHost,"/compile"),
					new PathPair(config.getLibRoot(),"/testlib") },
					"/compile/compile-solution.sh /code /testlib",
					imageName,
					null, -1);			
			return new CompilationResponse(r.isSuccess(),r.getResponse(),r.getExecutionTimeMs());
		} catch (ContainerKilledException e) {
			return new CompilationResponse(false,e.getMessage(),e.getExecutionTimeMs());
		}
	}

	public HarnessResponse execHarness(File codeDirHost, File harnessRecipeDirHost, String imageName, int timeoutSec) {
		try {
			ExecResponse r = exec_container(new PathPair[] { 
					new PathPair(codeDirHost,"/code"),
					new PathPair(harnessRecipeDirHost,"/harness"),
					new PathPair(config.getLibRoot(),"/testlib") },
					"/harness/run-harness.sh /code /harness /testlib",
					imageName,
					null, timeoutSec);
			
			ObjectMapper objectMapper = new ObjectMapper();
			try {
				return objectMapper.readValue(r.getResponse(),HarnessResponse.class);
			} catch (IOException e) {
				return new HarnessResponse("Failed to parse response ("+e.getMessage()+"): "+r.getResponse(),r.getExecutionTimeMs());
			}
		} catch (ContainerKilledException e) {
			return new HarnessResponse(e.getMessage(),e.getExecutionTimeMs());
		}		
	}

	public ValidationResponse execValidator(File validatorDirectory, HarnessResponse harnessResponse,
			String imageName) {
		ObjectMapper o = new ObjectMapper();
		try {
			ExecResponse r = exec_container(new PathPair[] { 
					new PathPair(validatorDirectory,"/validator"),
					new PathPair(config.getLibRoot(),"/testlib") },
					"/validator/run-validator.sh /validator /testlib",
					imageName,
					o.writeValueAsString(harnessResponse)+"\n\n", -1);
			try {
				return o.readValue(r.getResponse(),ValidationResponse.class);
			} catch (IOException e) {
				return new ValidationResponse("Failed to parse response ("+e.getMessage()+"): "+r.getResponse(),r.getExecutionTimeMs());
			}
		} catch (JsonProcessingException e) {
			return new ValidationResponse("Failed to serialise harness response: "+e.getMessage(),-1);
		} catch (ContainerKilledException e) {
			return new ValidationResponse(e.getMessage(),e.getExecutionTimeMs());
		}

	}

	

}
