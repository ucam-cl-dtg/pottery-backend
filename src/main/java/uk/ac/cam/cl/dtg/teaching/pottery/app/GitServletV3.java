/*
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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.io.IOException;
import java.io.PrintWriter;
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
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RetiredTaskException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.task.BuilderInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.task.Task;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskIndex;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Worker;

@SuppressWarnings("serial")
@WebServlet(
  urlPatterns = {"/git/*"},
  initParams = {
    @WebInitParam(name = "base-path", value = "/opt/pottery/tasks/def"),
    @WebInitParam(name = "export-all", value = "true")
  }
)
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
                new PostReceiveHook() {
                  @Override
                  public void onPostReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
                    String repoName = req.getPathInfo().substring(1);
                    LOG.info("Received push to {}", repoName);
                    TaskIndex t =
                        GuiceResteasyBootstrapServletContextListenerV3.getInjector()
                            .getInstance(TaskIndex.class);
                    Worker w =
                        GuiceResteasyBootstrapServletContextListenerV3.getInjector()
                            .getInstance(Worker.class);
                    try (PrintWriter output =
                        new PrintWriter(pack.getMessageOutputStream(), true)) {
                      try {
                        Task task = t.getTask(repoName);
                        BuilderInfo b = task.scheduleBuildTestingCopy(w);
                        output.println("Waiting for testing build of task...");
                        int previousStatus = 0;

                        while (previousStatus < 6) {
                          int currentStatus = BuilderInfo.statusToInt(b.getStatus());

                          if (currentStatus >= 2 && previousStatus < 2) {
                            output.println("Copying files");
                          }

                          if (currentStatus >= 3 && previousStatus < 3) {
                            output.println("Compiling test");
                          }

                          if (currentStatus >= 4 && previousStatus < 4) {
                            output.println(b.getTestCompileResponse());
                            output.println("Compiling solution");
                          }

                          if (currentStatus >= 5 && previousStatus < 5) {
                            output.println(b.getSolutionCompileResponse());
                            output.println("Testing solution");
                          }

                          if (currentStatus >= 6 && previousStatus < 6) {
                            ObjectMapper o = new ObjectMapper();
                            o.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
                            ObjectWriter writer = o.writerWithDefaultPrettyPrinter();
                            try {
                              writer.writeValue(output, b.getHarnessResponse());
                            } catch (IOException e) {
                              output.println(
                                  "Failed to serialise harness output: " + e.getMessage());
                            }
                            try {
                              writer.writeValue(output, b.getValidatorResponse());
                            } catch (IOException e) {
                              output.println(
                                  "Failed to serialise validator output: " + e.getMessage());
                            }
                          }
                          previousStatus = currentStatus;
                          Thread.sleep(1000);
                        }

                        if (b.getStatus().equals(BuilderInfo.STATUS_FAILURE)) {
                          output.println("Failed");
                          if (b.getException() != null) {
                            b.getException().printStackTrace(output);
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
                  }
                });
            return pack;
          }
        });

    super.init(config);
  }
}
