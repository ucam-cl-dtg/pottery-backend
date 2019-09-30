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
package uk.ac.cam.cl.dtg.teaching.pottery.containers.taint;

public class Taint {
  private final String name;
  private final boolean userControlled;

  public Taint(String name, boolean userControlled) {
    this.name = name;
    this.userControlled = userControlled;
  }

  public boolean isUserControlled() {
    return userControlled;
  }

  public String name() {
    return name;
  }

  public String identity() {
    if (userControlled) {
      return name;
    }
    return null;
  }

  @Override
  public String toString() {
    return userControlled ? "Tainted(" + name + ")" : "Untainted";
  }

  public static final Taint Compile = new Taint("compile", false);

  public static Taint parameterisation(String repoId) {
    return new Taint(repoId, false);
  }

  public static Taint userControlled(Taint taint) {
    if (taint.isUserControlled()) {
      return taint;
    }
    return new Taint(taint.name, true);
  }
}
