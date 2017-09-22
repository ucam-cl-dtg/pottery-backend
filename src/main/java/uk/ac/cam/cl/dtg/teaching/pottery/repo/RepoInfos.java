package uk.ac.cam.cl.dtg.teaching.pottery.repo;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanHandler;
import uk.ac.cam.cl.dtg.teaching.pottery.model.RepoInfo;

import java.sql.SQLException;
import java.sql.Timestamp;

public class RepoInfos {
  public static RepoInfo getByRepoId(String repoId, QueryRunner q) throws SQLException {
    return q.query(
        "SELECT * from repos where repoid=?", new BeanHandler<RepoInfo>(RepoInfo.class), repoId);
  }

  public static void insert(RepoInfo repoInfo, QueryRunner q) throws SQLException {
    q.update(
        "INSERT INTO repos(repoid,taskid,using_testing_version,expiryDate) values (?,?,?,?)",
        repoInfo.getRepoId(),
        repoInfo.getTaskId(),
        repoInfo.isUsingTestingVersion(),
        new Timestamp(repoInfo.getExpiryDate().getTime()));
  }
}
