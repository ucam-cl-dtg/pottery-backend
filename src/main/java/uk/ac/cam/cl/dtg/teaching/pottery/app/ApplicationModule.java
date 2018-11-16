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
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.wordnik.swagger.jaxrs.config.BeanConfig;
import com.wordnik.swagger.jaxrs.listing.ApiDeclarationProvider;
import com.wordnik.swagger.jaxrs.listing.ApiListingResource;
import com.wordnik.swagger.jaxrs.listing.ApiListingResourceJSON;
import com.wordnik.swagger.jaxrs.listing.ResourceListingProvider;
import java.util.Enumeration;
import java.util.Properties;
import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import javax.servlet.ServletContext;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.util.FS;
import uk.ac.cam.cl.dtg.teaching.cors.CorsRequestFilter;
import uk.ac.cam.cl.dtg.teaching.cors.CorsResponseFilter;
import uk.ac.cam.cl.dtg.teaching.exceptions.ExceptionHandler;
import uk.ac.cam.cl.dtg.teaching.pottery.config.ContainerEnvConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.config.RepoConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.config.TaskConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerBackend;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.DockerContainerImpl;
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
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskIndex;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.ThreadPoolWorker;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Worker;

public class ApplicationModule implements Module {

  private ServletContext context;

  ApplicationModule(ServletContext context) {
    this.context = context;
    BeanConfig beanConfig = new BeanConfig();
    beanConfig.setVersion("1.0.0");
    beanConfig.setBasePath("/pottery-backend/api");
    beanConfig.setResourcePackage("uk.ac.cam.cl.dtg.teaching.pottery.controllers");
    beanConfig.setScan(true);
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

    binder.bind(Database.class).to(PostgresDatabase.class).in(Singleton.class);
    binder.bind(Worker.class).to(ThreadPoolWorker.class).in(Singleton.class);
    binder.bind(Worker.class).annotatedWith(Names.named(Repo.PARAMETERISATION_WORKER_NAME))
        .to(ThreadPoolWorker.class).in(Singleton.class);
    binder.bind(ContainerBackend.class).to(DockerContainerImpl.class).in(Singleton.class);

    binder.bind(GuiceDependencyController.class);

    Enumeration<String> names = context.getInitParameterNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      binder.bindConstant().annotatedWith(Names.named(name)).to(context.getInitParameter(name));
    }

    SshSessionFactory.setInstance(
        new JschConfigSessionFactory() {
          @Override
          protected void configure(OpenSshConfig.Host hc, Session session) {
            // do nothing
          }

          @Override
          protected JSch createDefaultJSch(FS fs) throws JSchException {
            JSch defaultJSch = super.createDefaultJSch(fs);
            defaultJSch.removeAllIdentity();
            defaultJSch.addIdentity(context.getInitParameter("sshPrivateKey"));
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            JSch.setConfig(config);
            return defaultJSch;
          }
        });
  }

  /** Stop the various worker and manager threads. */
  @PreDestroy
  public void preDestroy() {
    Injector injector = GuiceResteasyBootstrapServletContextListenerV3.getInjector();
    injector.getInstance(Worker.class).stop();
    injector.getInstance(ContainerManager.class).stop();
    injector.getInstance(Database.class).stop();

    // TODO: this is plausible but needs one of two possible fixes:
    // 1) we need to avoid instantiating things that are not instanatiated already
    // or 2) we need to tear things down in a sensible order, objects should be closed before their
    // dependents
    // Currently the issue is that we close e.g. Database and then instantiate e.g. Worker which in
    // turn dependes on something with a constructor that needs a working database
    /*
    for (Map.Entry<Key<?>, Binding<?>> e : injector.getAllBindings().entrySet()) {
      Class<?> rawType = e.getKey().getTypeLiteral().getRawType();
      Binding<?> binding = e.getValue();
      if (Stoppable.class.isAssignableFrom(rawType)) {
        binding.acceptScopingVisitor(
            new DefaultBindingScopingVisitor<Void>() {

              @Override
              public Void visitScope(Scope scope) {
                if (scope == Scopes.SINGLETON) {
                  // TODO: this instantiates the singleton if it hasn't been already ;-(
                  ((Stoppable) (binding.getProvider().get())).stop();
                }
                return null;
              }

              @Override
              public Void visitEagerSingleton() {
                ((Stoppable) (binding.getProvider().get())).stop();
                return null;
              }
            });
      }
    }
    */
  }
}
