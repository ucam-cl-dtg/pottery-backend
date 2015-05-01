package uk.ac.cam.cl.dtg.teaching.pottery.controllers;

import java.io.IOException;
import java.util.Collection;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.cam.cl.dtg.teaching.pottery.Repo;
import uk.ac.cam.cl.dtg.teaching.pottery.SourceManager;
import uk.ac.cam.cl.dtg.teaching.pottery.Store;
import uk.ac.cam.cl.dtg.teaching.pottery.Task;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskNotFoundException;

import com.google.inject.Inject;

@Produces("application/json")
@Path("/tasks")
public class TasksController {

	private static final Logger log = LoggerFactory.getLogger(TasksController.class);
	
	@Inject
	Store store;
	
	@Inject
	SourceManager repoManager;
	
	@GET
	@Path("/")
	public Collection<Task> listAllTasks() {
		return store.tasks.values();
	}

	@POST
	@Path("/{taskId}")
	public Repo makeRepo(@PathParam("taskId") String taskId) throws TaskNotFoundException, RepoException, IOException {
		Task t = store.tasks.get(taskId);
		if (t == null) throw new TaskNotFoundException();

		String repoId = repoManager.createRepo();
		
		Repo r = new Repo();
		r.setTaskId(taskId);
		r.setRepoId(repoId);
		store.repos.put(r.getRepoId(),r);
		return r;
	}
}
