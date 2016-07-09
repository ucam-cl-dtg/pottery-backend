package uk.ac.cam.cl.dtg.teaching.pottery.app;

import java.util.Collection;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;

import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.http.server.resolver.DefaultReceivePackFactory;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskCloneException;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskManager;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Worker;

@SuppressWarnings("serial")
@WebServlet(urlPatterns = { "/git/*" }, 
initParams = { 
		@WebInitParam(name = "base-path", value="/opt/pottery/tasks/def"),
		@WebInitParam(name = "export-all", value="true")
})
public class GitServletV3 extends GitServlet {

	protected static final Logger LOG = LoggerFactory.getLogger(GitServletV3.class);
	
	@Override
	public void init(ServletConfig config) throws ServletException {
	
		setReceivePackFactory(new DefaultReceivePackFactory() {
			@Override
			public ReceivePack create(HttpServletRequest req, Repository db)
					throws ServiceNotEnabledException, ServiceNotAuthorizedException {
				ReceivePack pack = super.create(req, db);
				pack.setPostReceiveHook(new PostReceiveHook() {
					@Override
					public void onPostReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
						String repoName = req.getPathInfo().substring(1);
						LOG.info("Received push to {}",repoName);
						TaskManager t = GuiceResteasyBootstrapServletContextListenerV3.getInjector().getInstance(TaskManager.class);
						Worker w = GuiceResteasyBootstrapServletContextListenerV3.getInjector().getInstance(Worker.class);
						try {
							t.getTask(repoName).scheduleBuildTestingCopy(w);
						} catch (TaskCloneException e) {
							LOG.error("Failed to update testing checkout in {}",repoName,e);
						}
					}
				});
				return pack;
			}
			
		});
		
		super.init(config);
	}

	
	
}

