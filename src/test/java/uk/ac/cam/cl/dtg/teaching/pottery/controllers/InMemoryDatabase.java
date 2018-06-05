/*
 * pottery-backend - Backend API for testing programming exercises
 * Copyright © 2015-2018 Andrew Rice (acr31@cam.ac.uk), BlueOptima Limited
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

package uk.ac.cam.cl.dtg.teaching.pottery.controllers;

import com.mchange.v2.c3p0.DataSources;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import uk.ac.cam.cl.dtg.teaching.pottery.TransactionQueryRunner;
import uk.ac.cam.cl.dtg.teaching.pottery.database.Database;

public class InMemoryDatabase implements Database {

  private static AtomicInteger counter = new AtomicInteger();

  private DataSource dataSource;

  /** Create a new instance (independent of all others). */
  public InMemoryDatabase() throws IOException {
    try {
      dataSource =
          DataSources.unpooledDataSource("jdbc:hsqldb:mem:" + counter.incrementAndGet(), "SA", "");
      try (TransactionQueryRunner queryRunner = getQueryRunner()) {
        byte[] encoded = Database.class.getResourceAsStream("schema.sql").readAllBytes();
        String shape = new String(encoded);
        shape = Pattern.compile("--.*$", Pattern.MULTILINE).matcher(shape).replaceAll("");
        for(String query : shape.split(";")) {
          query = query.trim();
          if (query.toUpperCase().startsWith("CREATE TABLE")) {
            query = query.replaceAll("text", "character varying(65536)");
            query = query.replaceAll("::integer", "");
            queryRunner.update(query);
          }
        }
        queryRunner.commit();
      }
    } catch (SQLException e) {
      throw new IOException("Couldn't create testing database", e);
    }
  }

  @Override
  public TransactionQueryRunner getQueryRunner() throws SQLException {
    return new TransactionQueryRunner(dataSource);
  }

  @Override
  public void stop() {}
}
