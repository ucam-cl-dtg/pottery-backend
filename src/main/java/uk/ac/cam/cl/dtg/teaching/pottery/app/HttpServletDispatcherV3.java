package uk.ac.cam.cl.dtg.teaching.pottery.app;

import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;

import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;

@SuppressWarnings("serial")
@WebServlet(urlPatterns = { "/api/*" }, 
initParams = { 
		@WebInitParam(name = "resteasy.servlet.mapping.prefix", value="/api/")
})
public class HttpServletDispatcherV3 extends HttpServletDispatcher {
}
