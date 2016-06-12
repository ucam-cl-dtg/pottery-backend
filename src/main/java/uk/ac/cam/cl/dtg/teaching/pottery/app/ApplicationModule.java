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
import uk.ac.cam.cl.dtg.teaching.pottery.controllers.CleanupController;
import uk.ac.cam.cl.dtg.teaching.pottery.controllers.GuiceDependencyController;
import uk.ac.cam.cl.dtg.teaching.pottery.controllers.RepoController;
import uk.ac.cam.cl.dtg.teaching.pottery.controllers.SubmissionsController;
import uk.ac.cam.cl.dtg.teaching.pottery.controllers.TasksController;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.RepoFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskManager;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Worker;

public class ApplicationModule implements Module {

	@Override
	public void configure(Binder binder) {
		binder.bind(SubmissionsController.class);
		binder.bind(RepoController.class);
		binder.bind(TasksController.class);
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
        binder.bind(TaskManager.class).in(Singleton.class);
        binder.bind(ContainerManager.class).in(Singleton.class);

        binder.bind(TaskConfig.class).in(Singleton.class);
        binder.bind(RepoConfig.class).in(Singleton.class);
        binder.bind(ContainerEnvConfig.class).in(Singleton.class);
        
        binder.bind(Database.class).in(Singleton.class);
        binder.bind(CleanupController.class);
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
