package uk.ac.cam.cl.dtg.teaching.pottery.config;

import java.io.File;

public class RepoConfig {

	private File prefix;

	public RepoConfig() {
		this.prefix = new File(Config.PREFIX,"repos");
	}
	
	public RepoConfig(File repoPrefix) {
		this.prefix = repoPrefix;
	}
	
	public File getRepoRoot() {
		return new File(prefix,"repos");
	}
	
	public File getRepoTestingRoot() {
		return new File(prefix,"repo-testing");
	}
	
	public String getWebtagPrefix() {
		return "online-";
	}

}