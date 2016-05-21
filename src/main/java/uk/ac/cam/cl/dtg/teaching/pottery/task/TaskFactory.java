package uk.ac.cam.cl.dtg.teaching.pottery.task;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import uk.ac.cam.cl.dtg.teaching.pottery.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.UUIDGenerator;
import uk.ac.cam.cl.dtg.teaching.pottery.app.Config;
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
					return Task.openTask(key, database, config);
				}
			});

	private Config config;
	
	private Database database;
	
	@Inject
	public TaskFactory(Config config, Database database) {
		this.config = config;
		this.database = database;
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
					return Task.createTask(newRepoId, config, database);			
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
