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

import com.google.common.collect.ImmutableList;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import java.util.List;
import java.util.Objects;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.annotation.WebListener;
import org.jboss.resteasy.plugins.guice.GuiceResteasyBootstrapServletContextListener;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.database.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.Repo;
import uk.ac.cam.cl.dtg.teaching.pottery.ssh.SshManager;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskIndex;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Worker;

@WebListener
public class GuiceResteasyBootstrapServletContextListenerV3
    extends GuiceResteasyBootstrapServletContextListener {

  private static Injector injector;

  private static Injector getInjector() {
    return injector;
  }

  public static TaskIndex getTaskIndex() {
    return getInjector().getInstance(TaskIndex.class);
  }

  public static SshManager getSshManager() {
    return getInjector().getInstance(SshManager.class);
  }

  public static Worker getGeneralWorker() {
    return getInjector().getInstance(Key.get(Worker.class, Names.named(Repo.GENERAL_WORKER)));
  }

  public static void stop() {
    injector.findBindingsByType(TypeLiteral.get(Worker.class)).stream()
        .map(binding -> binding.getProvider().get())
        .filter(Objects::nonNull)
        .forEach(Worker::stop);
    injector.getInstance(ContainerManager.class).stop();
    injector.getInstance(Database.class).stop();
  }

  @Override
  public void contextInitialized(ServletContextEvent event) {
    super.contextInitialized(event);
  }

  @Override
  protected List<? extends Module> getModules(ServletContext context) {
    return ImmutableList.of(new ApplicationModule(context));
  }

  @Override
  protected void withInjector(Injector i) {
    injector = i;
  }
}
