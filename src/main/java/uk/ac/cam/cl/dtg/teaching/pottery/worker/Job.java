package uk.ac.cam.cl.dtg.teaching.pottery.worker;

import uk.ac.cam.cl.dtg.teaching.pottery.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.RepoFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskManager;

public interface Job {

	public void execute(TaskManager taskManager, RepoFactory repoFactory, ContainerManager containerManager, Database database) throws Exception;
}
