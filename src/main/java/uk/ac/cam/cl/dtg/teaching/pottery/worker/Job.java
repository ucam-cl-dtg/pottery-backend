package uk.ac.cam.cl.dtg.teaching.pottery.worker;

import uk.ac.cam.cl.dtg.teaching.docker.api.DockerApi;
import uk.ac.cam.cl.dtg.teaching.pottery.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.app.Config;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.RepoFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskManager;

public interface Job {

	public void execute(TaskManager taskManager, RepoFactory repoFactory, DockerApi docker, Database database, Config config) throws Exception;
}
