package uk.ac.cam.cl.dtg.teaching.pottery.app;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.wordnik.swagger.jaxrs.config.BeanConfig;
import com.wordnik.swagger.jaxrs.listing.ApiDeclarationProvider;
import com.wordnik.swagger.jaxrs.listing.ApiListingResource;
import com.wordnik.swagger.jaxrs.listing.ApiListingResourceJSON;
import com.wordnik.swagger.jaxrs.listing.ResourceListingProvider;

import uk.ac.cam.cl.dtg.teaching.cors.CorsRequestFilter;
import uk.ac.cam.cl.dtg.teaching.cors.CorsResponseFilter;
import uk.ac.cam.cl.dtg.teaching.docker.Docker;
import uk.ac.cam.cl.dtg.teaching.docker.api.DockerApi;
import uk.ac.cam.cl.dtg.teaching.exceptions.ExceptionHandler;
import uk.ac.cam.cl.dtg.teaching.pottery.Database;
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
	
	@Provides @Singleton
	DockerApi provideDockerApi() {
		return new Docker("localhost",2375).api();
	}
	
	public ApplicationModule() {
		BeanConfig beanConfig = new BeanConfig();
		beanConfig.setVersion("1.0.0");
		beanConfig.setBasePath("/pottery-backend/api");
		beanConfig.setResourcePackage("uk.ac.cam.cl.dtg.teaching.pottery.controllers");
		beanConfig.setScan(true);
	}
	
}
