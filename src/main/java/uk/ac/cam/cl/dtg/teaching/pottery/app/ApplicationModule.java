package uk.ac.cam.cl.dtg.teaching.pottery.app;

import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.cam.cl.dtg.teaching.docker.Docker;
import uk.ac.cam.cl.dtg.teaching.docker.api.DockerApi;
import uk.ac.cam.cl.dtg.teaching.exceptions.ExceptionHandler;
import uk.ac.cam.cl.dtg.teaching.pottery.BinaryManager;
import uk.ac.cam.cl.dtg.teaching.pottery.SourceManager;
import uk.ac.cam.cl.dtg.teaching.pottery.Store;
import uk.ac.cam.cl.dtg.teaching.pottery.controllers.RepoController;
import uk.ac.cam.cl.dtg.teaching.pottery.controllers.SubmissionsController;
import uk.ac.cam.cl.dtg.teaching.pottery.controllers.TasksController;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.mongodb.DB;
import com.mongodb.MongoClient;

public class ApplicationModule implements Module {

	private static final Logger log = LoggerFactory.getLogger(ApplicationModule.class);
	
	@Override
	public void configure(Binder binder) {
		binder.bind(TasksController.class);
		binder.bind(SubmissionsController.class);
		binder.bind(RepoController.class);
		binder.bind(TasksController.class);
		binder.bind(ExceptionHandler.class);
		binder.bind(CorsResponseFilter.class);
		binder.bind(AuthenticationPrincipalInterceptor.class);
	}
	
	@Provides @Singleton
	DockerApi provideDockerApi() {
		return new Docker("localhost",2375).api();
	}
	
	private DB db;
	private SourceManager sourceManager;
	public ApplicationModule() {
		try {
			db = new MongoClient("localhost").getDB("ptest");
		} catch (UnknownHostException e) {
			log.error("Failed to open database",e);
		}
		sourceManager = new SourceManager();
	}
	
	@Provides @Singleton
	DB provideMongoDB() throws UnknownHostException {
		return db;
	}
	
	@Provides @Singleton
	Store provideStore()  {
		return new Store(sourceManager);
	}
	
	
	
	@Provides @Singleton
	SourceManager provideSourceManager() {
		return sourceManager;
	}
	
	@Provides @Singleton
	BinaryManager provideBinaryManager() {
		return new BinaryManager(db);
	}
}
