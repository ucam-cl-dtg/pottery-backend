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

public class SubmissionAlreadyScheduledException extends Exception {

  private static final long serialVersionUID = 1L;

  public SubmissionAlreadyScheduledException() {
    // TODO Auto-generated constructor stub
  }

  public SubmissionAlreadyScheduledException(String message) {
    super(message);
    // TODO Auto-generated constructor stub
  }

  public SubmissionAlreadyScheduledException(Throwable cause) {
    super(cause);
    // TODO Auto-generated constructor stub
  }

  public SubmissionAlreadyScheduledException(String message, Throwable cause) {
    super(message, cause);
    // TODO Auto-generated constructor stub
  }

  public SubmissionAlreadyScheduledException(
      String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
    // TODO Auto-generated constructor stub
  }
}
