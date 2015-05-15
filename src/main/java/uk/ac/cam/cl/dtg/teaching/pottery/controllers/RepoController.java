package uk.ac.cam.cl.dtg.teaching.pottery.controllers;

import java.io.IOException;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.cam.cl.dtg.teaching.pottery.Repo;
import uk.ac.cam.cl.dtg.teaching.pottery.RepoTag;
import uk.ac.cam.cl.dtg.teaching.pottery.SourceManager;
import uk.ac.cam.cl.dtg.teaching.pottery.Store;
import uk.ac.cam.cl.dtg.teaching.pottery.Task;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.FileData;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskNotFoundException;

import com.google.inject.Inject;


@Produces("application/json")
@Path("/repo")
public class RepoController {

	@Inject
	Store store;
	
	@Inject
	SourceManager repoManager;
	
	private static final Logger log = LoggerFactory.getLogger(RepoController.class);

	@GET 
	@Path("/{repoId}/{tag}")
	public List<String> listFiles(@PathParam("repoId") String repoId, @PathParam("tag") String tag) throws RepoException, IOException {
		return repoManager.listFiles(repoId, tag);
	}
	
	@GET
	@Path("/{repoId}/{tag}/{fileName: .+}")
	public Response readFile(@PathParam("repoId") String repoId, @PathParam("tag") String tag, @PathParam("fileName") String fileName) throws IOException, RepoException {
		StreamingOutput s = repoManager.readFile(repoId, tag, fileName);
		return Response.ok(s,MediaType.APPLICATION_OCTET_STREAM).build();
	}
	
	@POST
	@Consumes("multipart/form-data")
	@Path("/{repoId}/{tag}/{fileName: .+}")
	public void updateFile(@PathParam("repoId") String repoId, @PathParam("tag") String tag, @PathParam("fileName") String fileName, @MultipartForm FileData file) throws RepoException, IOException {
		if (!SourceManager.HEAD.equals(tag)) {
			throw new RepoException("Can only update files at HEAD revision");
		}
		repoManager.updateFile(repoId,fileName,file.getData());
	}
	
	@DELETE
	@Path("/{repoId}/{tag}/{fileName: .+}")
	public void deleteFile(@PathParam("repoId") String repoId, @PathParam("tag") String tag, @PathParam("fileName") String fileName) throws IOException {
		throw new IOException("Unimplemented");
	}
	
	@POST
	@Path("/{repoId}/restore/{tag}")
	public void restore(@PathParam("repoId") String repoId, @PathParam("tag") String tag) throws IOException {
		throw new IOException("Unimplemented");
	}
	
	@POST
	@Path("/{repoId}")
	public RepoTag tag(@PathParam("repoId") String repoId) throws IOException, RepoException {
		RepoTag r = new RepoTag();
		r.setTag(repoManager.newTag(repoId));
		return r;
	}
	
	@POST
	@Path("/")
	public Repo makeRepo(@FormParam("taskId") String taskId) throws TaskNotFoundException, RepoException, IOException {
		Task t = store.tasks.get(taskId);
		if (t == null) throw new TaskNotFoundException();

		String repoId = repoManager.createRepo();
		
		Repo r = new Repo();
		r.setTaskId(taskId);
		r.setRepoId(repoId);
		
		repoManager.copyFiles(repoId, Store.getSkeletonLocation(taskId));
		
		store.repos.put(r.getRepoId(),r);
		return r;
	}
	
}