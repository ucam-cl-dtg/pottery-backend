package uk.ac.cam.cl.dtg.teaching.pottery.app;

import java.io.IOException;
import java.security.Principal;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;

import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

@Provider
@ServerInterceptor
public class AuthenticationPrincipalInterceptor implements ContainerRequestFilter {

	@Override
	public void filter(ContainerRequestContext requestContext)
			throws IOException {
		SecurityContext securityContext = requestContext.getSecurityContext();
		if (securityContext != null) {
			Principal userPrincipal = securityContext.getUserPrincipal();
			if (userPrincipal != null) {
				ResteasyProviderFactory.pushContext(Principal.class, userPrincipal);
			}
		}
	}

}
