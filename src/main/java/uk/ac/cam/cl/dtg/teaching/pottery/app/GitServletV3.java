package uk.ac.cam.cl.dtg.teaching.pottery.app;

import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;

import org.eclipse.jgit.http.server.GitServlet;

@SuppressWarnings("serial")
@WebServlet(urlPatterns = { "/git/*" }, 
initParams = { 
		@WebInitParam(name = "base-path", value="/opt/pottery/tasks-bare"),
		@WebInitParam(name = "export-all", value="true")
})
public class GitServletV3 extends GitServlet {

}
