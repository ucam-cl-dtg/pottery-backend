package uk.ac.cam.cl.dtg.teaching.pottery.app;

import java.net.UnknownHostException;

import uk.ac.cam.cl.dtg.teaching.docker.Docker;
import uk.ac.cam.cl.dtg.teaching.docker.api.DockerApi;
import uk.ac.cam.cl.dtg.teaching.exceptions.ExceptionHandler;
import uk.ac.cam.cl.dtg.teaching.pottery.Store;
import uk.ac.cam.cl.dtg.teaching.pottery.controllers.ProgressController;
import uk.ac.cam.cl.dtg.teaching.pottery.controllers.SubmissionsController;
import uk.ac.cam.cl.dtg.teaching.pottery.controllers.TasksController;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.mongodb.DB;
import com.mongodb.MongoClient;

public class ApplicationModule implements Module {

	@Override
	public void configure(Binder binder) {
		binder.bind(TasksController.class);
		binder.bind(SubmissionsController.class);
		binder.bind(ProgressController.class);
		binder.bind(TasksController.class);
		binder.bind(ExceptionHandler.class);
		binder.bind(CorsResponseFilter.class);
		binder.bind(AuthenticationPrincipalInterceptor.class);
	}
	
	@Provides @Singleton
	DockerApi provideDockerApi() {
		return new Docker("localhost",2375).api();
	}
	
	@Provides @Singleton
	DB provideMongoDB() throws UnknownHostException {
		MongoClient client = new MongoClient("localhost");
		return client.getDB("ptest");
	}
	
	@Provides @Singleton
	Store provideStore()  {
		return new Store();
	}
	
	
}
