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
package uk.ac.cam.cl.dtg.teaching.pottery.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.apache.commons.dbutils.ResultSetHandler;
import uk.ac.cam.cl.dtg.teaching.pottery.Stoppable;
import uk.ac.cam.cl.dtg.teaching.pottery.TransactionQueryRunner;

public interface Database extends Stoppable {

  TransactionQueryRunner getQueryRunner() throws SQLException;

  @Override
  void stop();

  default Optional<String> lookupConfigValue(String key, TransactionQueryRunner t)
      throws SQLException {
    return t.query(
        "SELECT value from config where key=?",
        resultSet -> resultSet.next() ? Optional.of(resultSet.getString(1)) : Optional.empty(),
        key);
  }

  default void storeConfigValue(String key, String value, TransactionQueryRunner t)
      throws SQLException {
    int updates = t.update("UPDATE config set value =? where key=?", value, key);
    if (updates == 0) {
      t.insert("INSERT into config(key,value) values (?,?)", resultSet -> null, key, value);
    }
  }
}
