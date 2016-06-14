package uk.ac.cam.cl.dtg.teaching.pottery;

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

import com.google.inject.Singleton;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.mchange.v2.c3p0.DataSources;

@Singleton
public class Database implements Stoppable {
	
	private ComboPooledDataSource connectionPool;
	
	protected static final Logger log = LoggerFactory.getLogger(Database.class);

	public Database() {
		try {
			connectionPool = new ComboPooledDataSource();
			connectionPool.setDriverClass("org.postgresql.Driver"); 
			connectionPool.setJdbcUrl("jdbc:postgresql://localhost/pottery" );
			connectionPool.setUser("pottery");
			connectionPool.setPassword("pottery");

		} catch (PropertyVetoException e) {
			log.error("Failed to open database",e);
		}
	}
		
	public TransactionQueryRunner getQueryRunner() throws SQLException {
		return new TransactionQueryRunner(connectionPool);
	}
	
	public static int nextVal(String sequence, QueryRunner q) throws SQLException {
		return q.query("SELECT nextval('"+sequence+"')", new ResultSetHandler<Integer>() {
			@Override
			public Integer handle(ResultSet rs) throws SQLException {
				rs.next();
				return rs.getInt(1);
			}
		});
	}

	@Override
	public void stop() {		
		log.info("Closing connection pool");
		connectionPool.close();
		try {
			DataSources.destroy(connectionPool);
		} catch (SQLException e) {
			log.error("Error destroying connection pool",e);
		}
		
		String driverClass = connectionPool.getDriverClass();
	    Enumeration<Driver> drivers = DriverManager.getDrivers();
	    while (drivers.hasMoreElements()) {
	    	Driver driver = drivers.nextElement();
	    	if (driver.getClass().getName().equals(driverClass)) {
	    		try {
	    			log.info("Deregistering {}",driverClass);
	    			DriverManager.deregisterDriver(driver);
	    		} catch (SQLException ex) {
	    			log.error("Error deregistering JDBC driver {}", driver, ex);
	    		}
	    	} 
	    }
	}
	
}
