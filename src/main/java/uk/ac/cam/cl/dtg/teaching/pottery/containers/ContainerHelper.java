package uk.ac.cam.cl.dtg.teaching.pottery.containers;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import uk.ac.cam.cl.dtg.teaching.docker.DockerUtil;
import uk.ac.cam.cl.dtg.teaching.docker.api.DockerApi;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerConfig;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerResponse;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerStartConfig;
import uk.ac.cam.cl.dtg.teaching.docker.model.WaitResponse;
import uk.ac.cam.cl.dtg.teaching.pottery.app.Config;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.CompilationResponse;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.HarnessResponse;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.ValidationResponse;

public class ContainerHelper {

	public static final Logger LOG = LoggerFactory.getLogger(ContainerHelper.class);
	
	
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

	private static AtomicInteger counter = new AtomicInteger(0);

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
	
	public static ExecResponse exec_container(ContainerHelper.PathPair[] mapping, String command, String imageName, String stdin, DockerApi docker, Config serverConfig) {				
		try {
			String containerName = "pottery-"+counter.incrementAndGet();
			DockerUtil.deleteContainerByName(containerName,docker);
			ContainerConfig config = new ContainerConfig();
			config.setOpenStdin(true);
			config.setCmd(Arrays.asList("/usr/bin/sudo","-u","#"+serverConfig.getUid(),"/bin/bash","-c",command));
			config.setImage(imageName);
			Map<String,Map<String,String>> volumes = new HashMap<String,Map<String,String>>();
			for(ContainerHelper.PathPair p : mapping) {
				volumes.put(p.getContainer().getPath(), new HashMap<String,String>());
			}
			config.setVolumes(volumes);
			ContainerResponse response = docker.createContainer(containerName,config);
			try {
				String containerId = response.getId();
				ContainerStartConfig startConfig = new ContainerStartConfig();
				String[] binds = new String[mapping.length];
				for(int i=0;i<mapping.length;++i) {
					binds[i] = DockerUtil.bind(mapping[i].getHost(),mapping[i].getContainer());
				}
				startConfig.setBinds(binds);
				docker.startContainer(containerId,startConfig);
				StringBuffer output = new StringBuffer();
				AttachListener l = new AttachListener(output,stdin);
				docker.attach(response.getId(),true,true,true,true,true,l);
				WaitResponse waitResponse = docker.waitContainer(response.getId());
				boolean success = waitResponse.statusCode == 0;
				return new ExecResponse(success, output.toString());
			}
			finally {
				docker.deleteContainer(response.getId(), true, true);
			}
		} catch (RuntimeException e) {
			return new ExecResponse(false,e.getMessage());
		}
	}

	public static ExecResponse execTaskCompilation(File taskDirHost, String imageName, DockerApi docker,Config config) {
		ExecResponse r = exec_container(new PathPair[] {
				new PathPair(taskDirHost,"/task")
		}, "/task/compile-test.sh /task/test /task/harness /task/validator", imageName, null, docker,config);
		return r;
	}
	
	public static CompilationResponse execCompilation(File codeDirHost, File compilationRecipeDirHost, String imageName, DockerApi docker, Config config) {
		ExecResponse r = exec_container(new PathPair[] { 
				new PathPair(codeDirHost,"/code"),
				new PathPair(compilationRecipeDirHost,"/compile"),
				new PathPair(config.getLibRoot(),"/testlib") },
				"/compile/compile-solution.sh /code /testlib",
				imageName,
				null,
				docker,config);			
		return new CompilationResponse(r.isSuccess(),r.getResponse());
	}

	public static HarnessResponse execHarness(File codeDirHost, File harnessRecipeDirHost, String imageName, DockerApi docker, Config config) {
		ExecResponse r = exec_container(new PathPair[] { 
				new PathPair(codeDirHost,"/code"),
				new PathPair(harnessRecipeDirHost,"/harness"),
				new PathPair(config.getLibRoot(),"/testlib") },
				"/harness/run-harness.sh /code /harness /testlib",
				imageName,
				null,
				docker,config);
		
		ObjectMapper objectMapper = new ObjectMapper();
		try {
			return objectMapper.readValue(r.getResponse(),HarnessResponse.class);
		} catch (IOException e) {
			return new HarnessResponse("Failed to parse response ("+e.getMessage()+"): "+r.getResponse());
		}		
	}

	public static ValidationResponse execValidator(File validatorDirectory, HarnessResponse harnessResponse,
			String imageName, DockerApi docker,Config config) {
		ObjectMapper o = new ObjectMapper();
		try {
			ExecResponse r = exec_container(new PathPair[] { 
					new PathPair(validatorDirectory,"/validator"),
					new PathPair(config.getLibRoot(),"/testlib") },
					"/validator/run-validator.sh /validator /testlib",
					imageName,
					o.writeValueAsString(harnessResponse)+"\n\n",
					docker,config);
			try {
				return o.readValue(r.getResponse(),ValidationResponse.class);
			} catch (IOException e) {
				return new ValidationResponse("Failed to parse response ("+e.getMessage()+"): "+r.getResponse());
			}
		} catch (JsonProcessingException e) {
			return new ValidationResponse("Failed to serialise harness response: "+e.getMessage());
		}

	}

	

}
