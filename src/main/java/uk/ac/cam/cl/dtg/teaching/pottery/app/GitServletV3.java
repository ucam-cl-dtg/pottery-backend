/*
 * pottery-backend - Backend API for testing programming exercises
 * Copyright © 2015-2018 BlueOptima Limited, Andrew Rice (acr31@cam.ac.uk)
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

import java.io.PrintWriter;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.http.server.resolver.DefaultReceivePackFactory;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RetiredTaskException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.model.BuilderInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.task.Task;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskIndex;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Worker;

@SuppressWarnings("serial")
@WebServlet(
    urlPatterns = {"/git/*"},
    initParams = {
      @WebInitParam(name = "base-path", value = "/opt/pottery/tasks/def"),
      @WebInitParam(name = "export-all", value = "true")
    })
public class GitServletV3 extends GitServlet {

  protected static final Logger LOG = LoggerFactory.getLogger(GitServletV3.class);

  @Override
  public void init(ServletConfig config) throws ServletException {

    setReceivePackFactory(
        new DefaultReceivePackFactory() {
          @Override
          public ReceivePack create(HttpServletRequest req, Repository db)
              throws ServiceNotEnabledException, ServiceNotAuthorizedException {
            ReceivePack pack = super.create(req, db);
            pack.setPostReceiveHook(
                (rp, commands) -> {
                  String repoName = req.getPathInfo().substring(1);
                  LOG.info("Received push to {}", repoName);
                  TaskIndex t = GuiceResteasyBootstrapServletContextListenerV3.getTaskIndex();
                  Worker w = GuiceResteasyBootstrapServletContextListenerV3.getGeneralWorker();
                  try (PrintWriter output = new PrintWriter(pack.getMessageOutputStream(), true)) {
                    try {
                      Task task = t.getTask(repoName);
                      BuilderInfo b = task.scheduleBuildTestingCopy(w);
                      output.println("Waiting for testing build of task...");
                      int previousStatus = 0;

                      while (previousStatus < 5) {
                        Thread.sleep(1000);
                        int currentStatus = BuilderInfo.statusToInt(b.getStatus());

                        if (currentStatus >= 2 && previousStatus < 2) {
                          output.println("Copying files");
                        }

                        if (currentStatus >= 3 && previousStatus < 3) {
                          output.println("Compiling tests");
                        }

                        if (currentStatus >= 4 && previousStatus < 4) {
                          output.println(b.getTestCompileResponse());
                          output.println("Testing solutions");
                        }

                        if (currentStatus >= 5 && previousStatus < 5) {
                          output.println(b.getSolutionTestingResponse());
                        }

                        previousStatus = currentStatus;
                      }

                      if (b.getStatus().equals(BuilderInfo.STATUS_FAILURE)) {
                        output.println("Failed");
                        if (b.getException() != null) {
                          output.println(b.getException());
                          output.println(b.getException().getStackTrace()[0].toString());
                          output.println(b.getException().getCause());
                        }
                      } else {
                        output.println("Success");
                      }
                    } catch (TaskNotFoundException e) {
                      output.println("Task " + repoName + " not found");
                    } catch (RetiredTaskException e) {
                      output.println(
                          "Did not schedule build of testing copy - task "
                              + repoName
                              + " is retired");
                    } catch (InterruptedException e1) {
                      output.println("Interrupted waiting for completion");
                    }
                  }
                });
            return pack;
          }
        });

    super.init(config);
  }
}
