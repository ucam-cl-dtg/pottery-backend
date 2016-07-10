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

import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskNotFoundException;
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
						} catch (TaskNotFoundException e) {
							LOG.error("Task {} not found when triggering git post-update hook",repoName);
						}
					}
				});
				return pack;
			}
			
		});
		
		super.init(config);
	}

	
	
}

