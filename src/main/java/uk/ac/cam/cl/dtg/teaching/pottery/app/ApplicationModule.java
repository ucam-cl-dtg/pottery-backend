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

import javax.annotation.PreDestroy;

import com.google.inject.Binder;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.wordnik.swagger.jaxrs.config.BeanConfig;
import com.wordnik.swagger.jaxrs.listing.ApiDeclarationProvider;
import com.wordnik.swagger.jaxrs.listing.ApiListingResource;
import com.wordnik.swagger.jaxrs.listing.ApiListingResourceJSON;
import com.wordnik.swagger.jaxrs.listing.ResourceListingProvider;

import uk.ac.cam.cl.dtg.teaching.cors.CorsRequestFilter;
import uk.ac.cam.cl.dtg.teaching.cors.CorsResponseFilter;
import uk.ac.cam.cl.dtg.teaching.exceptions.ExceptionHandler;
import uk.ac.cam.cl.dtg.teaching.pottery.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.Stoppable;
import uk.ac.cam.cl.dtg.teaching.pottery.config.ContainerEnvConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.config.RepoConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.config.TaskConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.controllers.GuiceDependencyController;
import uk.ac.cam.cl.dtg.teaching.pottery.controllers.RepoController;
import uk.ac.cam.cl.dtg.teaching.pottery.controllers.SubmissionsController;
import uk.ac.cam.cl.dtg.teaching.pottery.controllers.TasksController;
import uk.ac.cam.cl.dtg.teaching.pottery.controllers.WorkerController;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.RepoFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskIndex;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Worker;

public class ApplicationModule implements Module {

	@Override
	public void configure(Binder binder) {
		binder.bind(SubmissionsController.class);
		binder.bind(RepoController.class);
		binder.bind(TasksController.class);
		binder.bind(WorkerController.class);
		binder.bind(ExceptionHandler.class);
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
        
        binder.bind(Database.class).in(Singleton.class);
        binder.bind(Worker.class).in(Singleton.class);
        
        binder.bind(GuiceDependencyController.class);
	}
	
	public ApplicationModule() {
		BeanConfig beanConfig = new BeanConfig();
		beanConfig.setVersion("1.0.0");
		beanConfig.setBasePath("/pottery-backend/api");
		beanConfig.setResourcePackage("uk.ac.cam.cl.dtg.teaching.pottery.controllers");
		beanConfig.setScan(true);
		
	}
	
	
	@PreDestroy
	public void preDestroy() {
		Injector injector = GuiceResteasyBootstrapServletContextListenerV3.getInjector();
		((Stoppable)injector.getInstance(Worker.class)).stop();
		((Stoppable)injector.getInstance(ContainerManager.class)).stop();
		((Stoppable)injector.getInstance(Database.class)).stop();
				
		// TODO: this is plausible but needs one of two possible fixes:
		// 1) we need to avoid instantiating things that are not instanatiated already
		// or 2) we need to tear things down in a sensible order, objects should be closed before their dependents
		// Currently the issue is that we close e.g. Database and then instantiate e.g. Worker which in turn dependes on something with a constructor that needs a working database
		/*
		for (Map.Entry<Key<?>, Binding<?>> e : injector.getAllBindings().entrySet()) {
			Class<?> rawType = e.getKey().getTypeLiteral().getRawType();
			Binding<?> binding = e.getValue();			
			if (Stoppable.class.isAssignableFrom(rawType)) {
				binding.acceptScopingVisitor(new DefaultBindingScopingVisitor<Void>() {

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
