package uk.ac.cam.cl.dtg.teaching.pottery.app;

import java.io.File;

public class Config {

	public File getRepoRoot() {
		return new File("/local/scratch/acr31/reporoot");
	}
	
	public File getTestingRoot() {
		return new File("/local/scratch/acr31/testingroot");
	}
	
	public String getHeadTag() {
		return "HEAD";
	}
	
	
	public String getWebtagPrefix() {
		return "online-";
	}
	
	public File getBinaryRoot() {
		return new File("/local/scratch/acr31/binaryroot");
	}
	
	public File getStorageLocation() {
		return new File("/local/scratch/acr31/pottery-problems");
	}
	
}
