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

import java.security.Principal;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;
import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
@ServerInterceptor
public class AuthenticationPrincipalInterceptor implements ContainerRequestFilter {

  protected static final Logger LOG =
      LoggerFactory.getLogger(AuthenticationPrincipalInterceptor.class);

  @Override
  public void filter(ContainerRequestContext requestContext) {
    SecurityContext securityContext = requestContext.getSecurityContext();
    if (securityContext != null) {
      Principal userPrincipal = securityContext.getUserPrincipal();
      if (userPrincipal != null) {
        LOG.info("User principal {}", userPrincipal.getName());
        ResteasyProviderFactory.pushContext(Principal.class, userPrincipal);
      }
    }
  }
}
