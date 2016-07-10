package uk.ac.cam.cl.dtg.teaching.pottery.repo;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import uk.ac.cam.cl.dtg.teaching.pottery.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.UUIDGenerator;
import uk.ac.cam.cl.dtg.teaching.pottery.config.RepoConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoStorageException;

@Singleton
public class RepoFactory {
	
	/**
	 * This object is used to generate new uuids for repos
	 */
	private UUIDGenerator uuidGenerator = new UUIDGenerator();
	
	private Database database;
	
	// We need to ensure that only onle Repo object exists for any repoId so that 
	// we guarantee mutual exclusion on the filesystem operations. So we cache created objects
	// here.
	private LoadingCache<String, Repo> cache = 
			CacheBuilder.newBuilder().
			softValues().
			build(new CacheLoader<String,Repo>() {
				@Override
				public Repo load(String key) throws Exception {
					return Repo.openRepo(key, config, database);
				}
			});


	private RepoConfig config;
	
	@Inject
	public RepoFactory(RepoConfig config, Database database) throws IOException {
		this.database = database;
		this.config = config;
		FileUtil.mkdir(config.getRepoRoot());
		FileUtil.mkdir(config.getRepoTestingRoot());
		for(File f : config.getRepoRoot().listFiles()) {
			if (f.getName().startsWith(".")) continue;
			String uuid = f.getName();
			uuidGenerator.reserve(uuid);
		}
	}
	
	public Repo getInstance(String repoId) throws RepoStorageException {
		try {
			return cache.get(repoId);
		} catch (ExecutionException e) {
			// this is thrown if an exception is thrown in the load method of the cache.
			if (e.getCause() instanceof RepoStorageException) {
				throw (RepoStorageException)e.getCause();
			}
			else {
				throw new Error(e);
			}
		}
	}

	public Repo createInstance(String taskId, boolean usingTestingVersion, Date expiryDate) throws RepoStorageException {
		final String newRepoId = uuidGenerator.generate();
		try {
			return cache.get(newRepoId, new Callable<Repo>() {
				@Override
				public Repo call() throws Exception {
					return Repo.createRepo(newRepoId, taskId,usingTestingVersion, expiryDate, config, database);			
				}
			});
		} catch (ExecutionException e) {
			if (e.getCause() instanceof RepoStorageException) {
				throw (RepoStorageException)e.getCause();
			}
			else {
				throw new Error(e);
			}
		}
	}
}
