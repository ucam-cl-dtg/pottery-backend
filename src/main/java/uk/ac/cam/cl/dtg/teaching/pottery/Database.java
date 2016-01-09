package uk.ac.cam.cl.dtg.teaching.pottery;

import java.beans.PropertyVetoException;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.apache.commons.dbutils.ResultSetHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Singleton;
import com.mchange.v2.c3p0.ComboPooledDataSource;

@Singleton
public class Database {
	
	private ComboPooledDataSource connectionPool;
	
	private static final Logger log = LoggerFactory.getLogger(Database.class);

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
	
	public int nextVal(String sequence, TransactionQueryRunner q) throws SQLException {
		return q.query("SELECT nextval('"+sequence+"')", new ResultSetHandler<Integer>() {
			@Override
			public Integer handle(ResultSet rs) throws SQLException {
				rs.next();
				return rs.getInt(1);
			}
		});
	}

}
