package uk.ac.cam.cl.dtg.teaching.pottery.task;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import uk.ac.cam.cl.dtg.teaching.pottery.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.UUIDGenerator;
import uk.ac.cam.cl.dtg.teaching.pottery.config.TaskConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerRestrictions;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.TaskInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskException;

@Singleton
public class TaskFactory {

	/**
	 * This object is used to generate new uuids for tasks
	 */
	private UUIDGenerator uuidGenerator = new UUIDGenerator();

	// Ensure that only only one Task object exists for any taskId so that 
	// we guarantee mutual exclusion on the filesystem operations. 
	private LoadingCache<String, Task> cache = 
			CacheBuilder.newBuilder().
			softValues().
			build(new CacheLoader<String,Task>() {
				@Override
				public Task load(String key) throws Exception {
					return Task.openTask(key, uuidGenerator, database, config);
				}
			});

	private TaskConfig config;
	
	private Database database;
	
	@Inject
	public TaskFactory(TaskConfig config, Database database) throws IOException, GitAPIException {
		this.config = config;
		this.database = database;
		FileUtil.mkdir(config.getTaskDefinitionRoot());
		FileUtil.mkdir(config.getTaskCopyRoot());
		FileUtil.mkdir(config.getTaskTemplateRoot());
		
		// TODO: need to implement task template support. For the meantime we only use
		// one template "standard" and if its not there use an empty stub
		// We need to make a commit into it too because otherwise you can't clone it later
		File stdTemplate = new File(config.getTaskTemplateRoot(),"standard");
		if (!stdTemplate.exists()) {
			try(Git g = Git.init().setDirectory(stdTemplate).call()) {
				TaskInfo i = new TaskInfo(
						"java", 
						"Template", 
						null, 
						"template:java", 
						"easy", 
						30, 
						"java", 
						"<p>Template task</p>",
						new ContainerRestrictions(),
						new ContainerRestrictions(),
						new ContainerRestrictions());
				i.save(stdTemplate);
				g.add().addFilepattern("task.json").call();
				g.commit().setMessage("Initial commit").call();
			}
		}
		
		for(File f : config.getTaskDefinitionRoot().listFiles()) {
			if (f.getName().startsWith(".")) continue;
			String uuid = f.getName();
			uuidGenerator.reserve(uuid);
		}
	}
	
	public Task getInstance(String taskId) throws TaskException {
		try{
			return cache.get(taskId);
		} catch (ExecutionException e) {
			// this is thrown if an exception is thrown in the load method of the cache.
			if (e.getCause() instanceof TaskException) {
				throw (TaskException)e.getCause();
			}
			else {
				throw new Error(e);
			}
		}
	}
	
	public Task createInstance() throws TaskException {
		final String newRepoId = uuidGenerator.generate();
		try {
			return cache.get(newRepoId, new Callable<Task>() {
				@Override
				public Task call() throws Exception {
					return Task.createTask(newRepoId,uuidGenerator, config, database);			
				}
			});
		} catch (ExecutionException e) {
			if (e.getCause() instanceof TaskException) {
				throw (TaskException)e.getCause();
			}
			else {
				throw new Error(e);
			}
		}
	}
	
}
