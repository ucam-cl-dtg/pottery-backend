package uk.ac.cam.cl.dtg.teaching.pottery;

import java.util.LinkedList;
import java.util.List;

/**
 * A collection of files in a directory structure which form the submission.
 * @author acr31
 *
 */
public class Code {

	private List<SourceFile> sourceFiles;

	public Code() {
		super();
		sourceFiles = new LinkedList<SourceFile>();
	}

	public void addSourceFile(SourceFile s) {
		sourceFiles.add(s);
	}

	public List<SourceFile> getSourceFiles() {
		return sourceFiles;
	}

	public void setSourceFiles(List<SourceFile> sourceFiles) {
		this.sourceFiles = sourceFiles;
	}
		
	
	
}
