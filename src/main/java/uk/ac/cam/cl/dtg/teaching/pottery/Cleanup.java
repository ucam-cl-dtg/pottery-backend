package uk.ac.cam.cl.dtg.teaching.pottery;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.commons.dbutils.ResultSetHandler;

import uk.ac.cam.cl.dtg.teaching.pottery.config.RepoConfig;

public class Cleanup {

	public static List<String> cleanup(Database database, RepoConfig config) throws SQLException, IOException {
	
		List<String> actionLog = new LinkedList<>();
		
		cleanupRepos(database, config, actionLog);
		
		// TODO: cleanup tasks
		
		return actionLog;
		
	}

	private static void cleanupRepos(Database database, RepoConfig config, List<String> actionLog)
			throws SQLException, IOException {
		File repoRoot = config.getRepoRoot();
		final Set<String> toRemoveFromFs = new HashSet<>();
		for(String repo : repoRoot.list(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return !name.startsWith(".");
			}
		})) {
			toRemoveFromFs.add(repo);
		}
		
		Set<String> toRemoveFromDb = new HashSet<>();
		
		try (TransactionQueryRunner r = database.getQueryRunner()) {
			r.query("SELECT repoid from repos", new ResultSetHandler<Void>() {
				@Override
				public Void handle(ResultSet rs) throws SQLException {
					while(rs.next()) {
						String repoid = rs.getString(1);
						if (!toRemoveFromFs.remove(repoid)) {
							toRemoveFromDb.add(repoid);
						}
					}
					return null;
				}
			});
			
			for(String repoid : toRemoveFromDb) {
				actionLog.add(repoid+": deleted from database");
				r.update("DELETE from repos where repoid=?",repoid);
			}
			
			for(String repoid : toRemoveFromFs) {
				actionLog.add(repoid+": deleted from filesystem");
				FileUtil.deleteRecursive(new File(repoRoot,repoid));
			}
			
			r.commit();
		}
	}
	
	
	
	
}
