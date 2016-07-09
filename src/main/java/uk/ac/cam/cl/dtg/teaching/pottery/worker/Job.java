package uk.ac.cam.cl.dtg.teaching.pottery.worker;

import uk.ac.cam.cl.dtg.teaching.pottery.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.RepoFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskManager;

public interface Job {

	/**
	 * Run the required task. 
	 * @param taskManager
	 * @param repoFactory
	 * @param containerManager
	 * @param database
	 * @return True if successful. This will cause the worker to schedule the next job in the sequence if there is one
	 * @throws Exception
	 */
	public boolean execute(TaskManager taskManager, RepoFactory repoFactory, ContainerManager containerManager, Database database) throws Exception;
}
