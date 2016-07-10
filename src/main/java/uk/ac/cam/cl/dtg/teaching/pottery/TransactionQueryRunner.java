/**
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

import java.io.Closeable;
import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionQueryRunner extends QueryRunner implements Closeable {

	protected static final Logger LOG = LoggerFactory.getLogger(TransactionQueryRunner.class);
	
	private Connection db;
	
	public TransactionQueryRunner(DataSource datasource) throws SQLException {
		db = datasource.getConnection();
		db.setAutoCommit(false);
	}

	public void commit() throws SQLException {
		db.commit();
	}
	
	public void rollback() throws SQLException {
		db.rollback();
	}
	
	public void close() {
		try {
			db.close();
		} catch (SQLException e) {
			LOG.warn("Exception (supressed) closing database connection",e);
		}
	}
	
	@Override
	public int[] batch(String sql, Object[][] params) throws SQLException {
		return super.batch(db,sql, params);
	}

	@Override
	public <T> T query(String sql, ResultSetHandler<T> rsh, Object... params) throws SQLException {
		return super.query(db, sql, rsh, params);
	}

	@Override
	public <T> T query(String sql, ResultSetHandler<T> rsh) throws SQLException {
		return super.query(db, sql, rsh);
	}

	@Override
	public int update(String sql) throws SQLException {
		return super.update(db,sql);
	}

	@Override
	public int update(String sql, Object param) throws SQLException {
		return super.update(db,sql, param);
	}

	@Override
	public int update(String sql, Object... params) throws SQLException {
		return super.update(db,sql, params);
	}

	@Override
	public <T> T insert(String sql, ResultSetHandler<T> rsh) throws SQLException {
		return super.insert(db, sql, rsh);
	}

	@Override
	public <T> T insert(String sql, ResultSetHandler<T> rsh, Object... params) throws SQLException {
		return super.insert(db, sql, rsh, params);
	}

	@Override
	public <T> T insertBatch(String sql, ResultSetHandler<T> rsh, Object[][] params) throws SQLException {
		return super.insertBatch(db, sql, rsh, params);
	}
	
	
}
