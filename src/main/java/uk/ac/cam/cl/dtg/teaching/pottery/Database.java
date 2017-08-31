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
package uk.ac.cam.cl.dtg.teaching.pottery;

import com.google.inject.Singleton;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.mchange.v2.c3p0.DataSources;
import java.beans.PropertyVetoException;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Enumeration;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Database implements Stoppable {

  private ComboPooledDataSource connectionPool;

  protected static final Logger LOG = LoggerFactory.getLogger(Database.class);

  public Database() {
    try {
      connectionPool = new ComboPooledDataSource();
      connectionPool.setDriverClass("org.postgresql.Driver");
      connectionPool.setJdbcUrl("jdbc:postgresql://localhost/pottery");
      connectionPool.setUser("pottery");
      connectionPool.setPassword("pottery");

    } catch (PropertyVetoException e) {
      LOG.error("Failed to open database", e);
    }
  }

  public TransactionQueryRunner getQueryRunner() throws SQLException {
    return new TransactionQueryRunner(connectionPool);
  }

  public static int nextVal(String sequence, QueryRunner q) throws SQLException {
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

  @Override
  public void stop() {
    LOG.info("Closing connection pool");
    connectionPool.close();
    try {
      DataSources.destroy(connectionPool);
    } catch (SQLException e) {
      LOG.error("Error destroying connection pool", e);
    }

    String driverClass = connectionPool.getDriverClass();
    Enumeration<Driver> drivers = DriverManager.getDrivers();
    while (drivers.hasMoreElements()) {
      Driver driver = drivers.nextElement();
      if (driver.getClass().getName().equals(driverClass)) {
        try {
          LOG.info("Deregistering {}", driverClass);
          DriverManager.deregisterDriver(driver);
        } catch (SQLException ex) {
          LOG.error("Error deregistering JDBC driver {}", driver, ex);
        }
      }
    }
  }
}
