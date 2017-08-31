/*
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

package uk.ac.cam.cl.dtg.teaching.pottery.exceptions;

import uk.ac.cam.cl.dtg.teaching.exceptions.HttpStatusCode404;

public class TaskNotFoundException extends Exception implements HttpStatusCode404 {

  private static final long serialVersionUID = 1L;

  public TaskNotFoundException() {
    super();
  }

  public TaskNotFoundException(
      String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

  public TaskNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }

  public TaskNotFoundException(String message) {
    super(message);
  }

  public TaskNotFoundException(Throwable cause) {
    super(cause);
  }
}
