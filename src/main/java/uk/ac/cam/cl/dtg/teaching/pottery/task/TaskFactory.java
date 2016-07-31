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
package uk.ac.cam.cl.dtg.teaching.pottery.task;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

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
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.InvalidTaskSpecificationException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskStorageException;

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
		File stdTemplate = config.getTaskTemplateDir("standard");
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
						ContainerRestrictions.candidateRestriction(null),
						ContainerRestrictions.candidateRestriction(null),
						ContainerRestrictions.candidateRestriction(null),
						ContainerRestrictions.authorRestriction(null),
						null);
				i.save(stdTemplate);
				g.add().addFilepattern("task.json").call();
				g.commit().setMessage("Initial commit").call();
			}
		}

		Stream.concat(
				Stream.of(config.getTaskDefinitionRoot().listFiles()), 
				Stream.of(config.getTaskCopyRoot().listFiles()))
			.filter(f -> !f.getName().startsWith("."))
			.forEach(f -> uuidGenerator.reserve(f.getName()));		
	}
	
	public Task getInstance(String taskId) throws InvalidTaskSpecificationException, TaskStorageException, TaskNotFoundException {
		try{
			return cache.get(taskId);
		} catch (ExecutionException e) {
			// this is thrown if an exception is thrown in the load method of the cache.
			if (e.getCause() instanceof InvalidTaskSpecificationException) {					
				throw (InvalidTaskSpecificationException)e.getCause();
			}
			else if (e.getCause() instanceof TaskStorageException) {
				throw (TaskStorageException)e.getCause();
			}
			else if (e.getCause() instanceof TaskNotFoundException) {
				throw (TaskNotFoundException)e.getCause();
			}
			else {
				throw new Error(e);
			}
		}
	}
	
	public Task createInstance() throws TaskStorageException  {
		final String newRepoId = uuidGenerator.generate();
		try {
			return cache.get(newRepoId, new Callable<Task>() {
				@Override
				public Task call() throws Exception {
					return Task.createTask(newRepoId,uuidGenerator, config, database);			
				}
			});
		} catch (ExecutionException e) {
			if (e.getCause() instanceof TaskStorageException) {					
				throw (TaskStorageException)e.getCause();
			}
			else {
				throw new Error(e);
			}
		}
	}
	
}
