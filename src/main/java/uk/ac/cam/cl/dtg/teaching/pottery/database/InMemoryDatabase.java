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

package uk.ac.cam.cl.dtg.teaching.pottery.database;

import com.mchange.v2.c3p0.DataSources;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import uk.ac.cam.cl.dtg.teaching.pottery.TransactionQueryRunner;

public class InMemoryDatabase implements Database {

  private static AtomicInteger counter = new AtomicInteger();

  private DataSource dataSource;
  private SQLException connectionException;

  public InMemoryDatabase() {
    try {
      dataSource =
          DataSources.unpooledDataSource("jdbc:hsqldb:mem:" + counter.incrementAndGet(), "SA", "");
      try (TransactionQueryRunner queryRunner = getQueryRunner()) {
        queryRunner.update(
            "CREATE TABLE repos ("
                + "repoid character varying(255) NOT NULL, "
                + "taskid character varying(255) NOT NULL, "
                + "using_testing_version boolean DEFAULT true NOT NULL, "
                + "expirydate timestamp with time zone"
                + ");");

        queryRunner.update(
            "CREATE TABLE submissions ("
                + "    repoid character varying(255) NOT NULL, "
                + "    tag character varying(255) NOT NULL, "
                + "    status character varying(255) NOT NULL, "
                + "    compilationoutput character varying(65536), "
                + "    compilationtimems bigint DEFAULT '-1' NOT NULL, "
                + "    harnesstimems bigint DEFAULT '-1' NOT NULL, "
                + "    validatortimems bigint DEFAULT '-1' NOT NULL, "
                + "    waittimems bigint DEFAULT '-1' NOT NULL, "
                + "    errormessage character varying(65536), "
                + "    teststeps character varying(65536), "
                + "    datescheduled timestamp without time zone, "
                + "    interpretation character varying(255)"
                + ");");
        queryRunner.update(
            "CREATE TABLE tasks ("
                + "    taskid character varying(255) NOT NULL, "
                + "    registeredtag character varying(255), "
                + "    retired boolean DEFAULT false NOT NULL, "
                + "    testingcopyid character varying(255), "
                + "    registeredcopyid character varying(255), "
                + "    remote character varying(255) default '' NOT NULL"
                + ");");
        queryRunner.commit();
      }
    } catch (SQLException e) {
      connectionException = e;
    }
  }

  @Override
  public TransactionQueryRunner getQueryRunner() throws SQLException {
    if (dataSource == null) {
      throw connectionException;
    }
    return new TransactionQueryRunner(dataSource);
  }

  @Override
  public void stop() {}
}
