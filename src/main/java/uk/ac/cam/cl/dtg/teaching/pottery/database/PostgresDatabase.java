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

import com.google.inject.Singleton;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.mchange.v2.c3p0.DataSources;
import java.beans.PropertyVetoException;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.TransactionQueryRunner;

@Singleton
public class PostgresDatabase implements Database {

  protected static final Logger LOG = LoggerFactory.getLogger(PostgresDatabase.class);
  private ComboPooledDataSource connectionPool;

  public PostgresDatabase() {
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

  @Override
  public TransactionQueryRunner getQueryRunner() throws SQLException {
    return new TransactionQueryRunner(connectionPool);
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
