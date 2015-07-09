package uk.ac.cam.cl.dtg.teaching.pottery.containers;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.IOUtils;

import uk.ac.cam.cl.dtg.teaching.docker.CommandBuilder;
import uk.ac.cam.cl.dtg.teaching.docker.DockerUtil;
import uk.ac.cam.cl.dtg.teaching.docker.api.DockerApi;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerConfig;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerResponse;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerStartConfig;
import uk.ac.cam.cl.dtg.teaching.docker.model.WaitResponse;

import com.pty4j.PtyProcess;

public class ContainerHelper {

	
	
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

	public static ExecResponse exec_container(ContainerHelper.PathPair[] mapping, String command, String imageName, String stdin, DockerApi docker) {				
		String containerName = "pottery-"+counter.incrementAndGet();
		DockerUtil.deleteContainerByName(containerName,docker);
			
		ContainerConfig config = new ContainerConfig();
		config.setAttachStderr(true);
		config.setAttachStdout(true);
		config.setAttachStdin(stdin != null);
		CommandBuilder cb = new CommandBuilder();
		cb.add(command);
		config.setCmd(cb.toCmd());
		config.setImage(imageName);
		Map<String,Map<String,String>> volumes = new HashMap<String,Map<String,String>>();
		for(ContainerHelper.PathPair p : mapping) {
			volumes.put(p.getContainer().getPath(), new HashMap<String,String>());
		}
		config.setVolumes(volumes);
		ContainerResponse response = docker.createContainer(containerName,config);
		String containerId = response.getId();
		ContainerStartConfig startConfig = new ContainerStartConfig();
		String[] binds = new String[mapping.length];
		for(int i=0;i<mapping.length;++i) {
			binds[i] = DockerUtil.bind(mapping[i].getHost(),mapping[i].getContainer());
		}
		startConfig.setBinds(binds);
		docker.startContainer(containerId,startConfig);
		DockerUtil.waitRunning(containerId, docker);
		StringWriter output = null;
		
		// the complexity here is that if you attach then program output gets sent there rather than stored 
		// in the logs.  This causes the program to block and it doesn't exit
		// however, we can't use attach every time because we might miss something in the gap between starting the 
		// container and attaching to it
		// therefore if there is input to be pushed in we assume that the program will block until its provided and so 
		// we don't need logs, we can just attach
		try {
			if (stdin != null) {
				PtyProcess p = PtyProcess.exec(new String[] { "docker.io","attach", containerId });
				Thread t = new Thread() {
					public void run() {
						try {
							IOUtils.write(stdin, p.getOutputStream());
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				};
				t.start();
				output = new StringWriter();
				IOUtils.copy(p.getInputStream(),output);
				p.destroy();
			}		
			System.out.println("Waiting for "+containerId);
			WaitResponse waitResponse = docker.waitContainer(containerId);
			boolean success = waitResponse.statusCode == 0;
			if (output == null) {
				output = new StringWriter();
				PtyProcess p = PtyProcess.exec(new String[] { "docker.io","logs", containerId });
				IOUtils.copy(p.getInputStream(),output);
				p.destroy();
			}
			return new ExecResponse(success,output.toString());
		} catch (IOException e) {
			e.printStackTrace();
			return new ExecResponse(false,null);
		}
		finally {
			docker.deleteContainer(response.getId(), true, true);
		}
	}

	public static ExecResponse execCompilation(File codeDirHost, File compilationRecipeDirHost, String imageName, DockerApi docker) {
		return exec_container(new PathPair[] { 
				new PathPair(codeDirHost,"/code"),
				new PathPair(compilationRecipeDirHost,"/compile") },
				"/compile/compile-solution.sh /code",
				imageName,
				null,
				docker);			
	}

	public static ExecResponse execHarness(File codeDirHost, File harnessRecipeDirHost, String imageName, DockerApi docker) {
		return exec_container(new PathPair[] { 
				new PathPair(codeDirHost,"/code"),
				new PathPair(harnessRecipeDirHost,"/harness") },
				"/harness/run-harness.sh /code /harness",
				imageName,
				null,
				docker);			
	}

	public static ExecResponse execValidator(File validatorDirectory, String harnessResult,
			String imageName, DockerApi docker) {
		return exec_container(new PathPair[] { 
				new PathPair(validatorDirectory,"/validator") },
				"/validator/run-validator.sh /validator",
				imageName,
				harnessResult,
				docker);			

	}

	

}
