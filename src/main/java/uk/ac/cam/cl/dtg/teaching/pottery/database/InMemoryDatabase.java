package uk.ac.cam.cl.dtg.teaching.pottery.database;

import com.mchange.v2.c3p0.DataSources;
import java.sql.SQLException;
import javax.sql.DataSource;
import uk.ac.cam.cl.dtg.teaching.pottery.TransactionQueryRunner;

public class InMemoryDatabase implements Database {

  private DataSource dataSource;
  private SQLException connectionException;

  public InMemoryDatabase() {
    try {
      dataSource = DataSources.unpooledDataSource("jdbc:hsqldb:mem:mymemdb","SA","");
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
  public void stop() {
  }
}
