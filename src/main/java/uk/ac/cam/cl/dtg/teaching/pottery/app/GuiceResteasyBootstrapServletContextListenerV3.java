package uk.ac.cam.cl.dtg.teaching.pottery.app;

import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.annotation.WebListener;

import org.jboss.resteasy.plugins.guice.GuiceResteasyBootstrapServletContextListener;

import com.google.inject.Injector;
import com.google.inject.Module;

@WebListener
public class GuiceResteasyBootstrapServletContextListenerV3 extends
		GuiceResteasyBootstrapServletContextListener {
		
	private static Injector injector;
	
	@Override
	public void contextInitialized(ServletContextEvent event) {
//		JGitInitialiser.init();		
		super.contextInitialized(event);
	}

	@Override
	protected List<? extends Module> getModules(ServletContext context) {
		return Arrays.asList(new Module[] { new ApplicationModule() });
	}

	@Override
	protected void withInjector(Injector i) {
		injector = i;
	}
	
	public static Injector getInjector() {
		return injector;
	}
}
