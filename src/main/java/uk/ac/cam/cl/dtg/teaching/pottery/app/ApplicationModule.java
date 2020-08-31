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

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.PrivateModule;
import com.google.inject.name.Names;
import com.wordnik.swagger.jaxrs.config.BeanConfig;
import com.wordnik.swagger.jaxrs.listing.ApiDeclarationProvider;
import com.wordnik.swagger.jaxrs.listing.ApiListingResource;
import com.wordnik.swagger.jaxrs.listing.ApiListingResourceJSON;
import com.wordnik.swagger.jaxrs.listing.ResourceListingProvider;
import java.util.Enumeration;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import javax.servlet.ServletContext;
import uk.ac.cam.cl.dtg.teaching.cors.CorsRequestFilter;
import uk.ac.cam.cl.dtg.teaching.cors.CorsResponseFilter;
import uk.ac.cam.cl.dtg.teaching.exceptions.ExceptionHandler;
import uk.ac.cam.cl.dtg.teaching.pottery.config.ContainerEnvConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.config.ContextKeys;
import uk.ac.cam.cl.dtg.teaching.pottery.config.DockerConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.config.RepoConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.config.TaskConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerBackend;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.DockerContainerImpl;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.DockerContainerWithReuseImpl;
import uk.ac.cam.cl.dtg.teaching.pottery.controllers.GuiceDependencyController;
import uk.ac.cam.cl.dtg.teaching.pottery.controllers.RepoController;
import uk.ac.cam.cl.dtg.teaching.pottery.controllers.StatusController;
import uk.ac.cam.cl.dtg.teaching.pottery.controllers.SubmissionsController;
import uk.ac.cam.cl.dtg.teaching.pottery.controllers.TasksController;
import uk.ac.cam.cl.dtg.teaching.pottery.controllers.WorkerController;
import uk.ac.cam.cl.dtg.teaching.pottery.database.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.database.PostgresDatabase;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.Repo;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.RepoFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.ssh.SshManager;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskIndex;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.ThreadPoolWorker;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Worker;

public class ApplicationModule implements Module {

  public static final String SSH_PRIVATE_KEY = "sshPrivateKey";
  private ServletContext context;

  ApplicationModule(ServletContext context) {
    this.context = context;
    BeanConfig beanConfig = new BeanConfig();
    beanConfig.setVersion("1.0.0");
    beanConfig.setBasePath("/pottery-backend/api");
    beanConfig.setResourcePackage("uk.ac.cam.cl.dtg.teaching.pottery.controllers");
    beanConfig.setScan(true);
  }

  private static class WorkerModule extends PrivateModule {

    private final String workerName;
    private final int initialThreads;
    private final Key<Worker> workerKey;

    WorkerModule(String workerName, int initialThreads) {
      this.workerName = workerName;
      this.initialThreads = initialThreads;
      this.workerKey = Key.get(Worker.class, Names.named(workerName));
    }

    @Override
    protected void configure() {
      bindConstant().annotatedWith(Names.named(Worker.WORKER_NAME)).to(workerName);
      bindConstant().annotatedWith(Names.named(Worker.INITIAL_POOL_SIZE)).to(initialThreads);
      bind(workerKey).to(ThreadPoolWorker.class).in(Singleton.class);
      expose(workerKey);
    }
  }

  @Override
  public void configure(Binder binder) {
    binder.bind(SubmissionsController.class);
    binder.bind(RepoController.class);
    binder.bind(TasksController.class);
    binder.bind(WorkerController.class);
    binder.bind(ExceptionHandler.class);
    binder.bind(StatusController.class);
    binder.bind(CorsResponseFilter.class);
    binder.bind(CorsRequestFilter.class);
    binder.bind(AuthenticationPrincipalInterceptor.class);
    binder.bind(ApiListingResource.class);
    binder.bind(ApiDeclarationProvider.class);
    binder.bind(ApiListingResourceJSON.class);
    binder.bind(ResourceListingProvider.class);
    binder.bind(RepoFactory.class).in(Singleton.class);
    binder.bind(TaskFactory.class).in(Singleton.class);
    binder.bind(TaskIndex.class).in(Singleton.class);
    binder.bind(ContainerManager.class).in(Singleton.class);

    binder.bind(TaskConfig.class).in(Singleton.class);
    binder.bind(RepoConfig.class).in(Singleton.class);
    binder.bind(ContainerEnvConfig.class).in(Singleton.class);
    binder.bind(DockerConfig.class).in(Singleton.class);

    binder.bind(Database.class).to(PostgresDatabase.class).in(Singleton.class);

    binder.bind(SshManager.class).in(Singleton.class);

    binder.install(
        new WorkerModule(
            Repo.GENERAL_WORKER,
            Integer.parseInt(context.getInitParameter(ContextKeys.GENERAL_POOL_INITIAL_THREADS))));
    binder.install(
        new WorkerModule(
            Repo.PARAMETERISATION_WORKER,
            Integer.parseInt(
                context.getInitParameter(ContextKeys.PARAMETERISATION_POOL_INITIAL_THREADS))));

    boolean reuseContainers =
        Boolean.parseBoolean(context.getInitParameter(ContextKeys.REUSE_CONTAINERS));
    binder
        .bind(ContainerBackend.class)
        .to(reuseContainers ? DockerContainerWithReuseImpl.class : DockerContainerImpl.class)
        .in(Singleton.class);

    binder.bind(GuiceDependencyController.class);

    Enumeration<String> names = context.getInitParameterNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      binder.bindConstant().annotatedWith(Names.named(name)).to(context.getInitParameter(name));
    }
  }

  @PostConstruct
  public void startSshManager() {
    GuiceResteasyBootstrapServletContextListenerV3.getSshManager().init();
  }

  /** Stop the various worker and manager threads. */
  @PreDestroy
  public void preDestroy() {
    GuiceResteasyBootstrapServletContextListenerV3.stop();
  }
}
