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

import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import uk.ac.cam.cl.dtg.teaching.pottery.Stoppable;
import uk.ac.cam.cl.dtg.teaching.pottery.TransactionQueryRunner;

public interface Database extends Stoppable {

  static int nextVal(String sequence, QueryRunner q) throws SQLException {
    return q.query(
        "SELECT nextval('" + sequence + "')",
        new ResultSetHandler<Integer>() {
          @Override
          public Integer handle(ResultSet rs) throws SQLException {
            rs.next();
            return rs.getInt(1);
          }
        });
  }

  TransactionQueryRunner getQueryRunner() throws SQLException;

  @Override
  void stop();
}
