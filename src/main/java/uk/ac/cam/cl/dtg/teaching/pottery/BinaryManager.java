package uk.ac.cam.cl.dtg.teaching.pottery;

import java.io.File;
import java.io.InputStream;

import com.mongodb.DB;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.gridfs.GridFSInputFile;

public class BinaryManager {

	public static final File BINARY_ROOT = new File("/local/scratch/acr31/binaryroot");
	
	private DB db;
	
	public BinaryManager(DB db) {
		this.db = db;
	}

	public void store(InputStream is, String key) {
		GridFS g = new GridFS(db,"binary");
		GridFSInputFile f = g.createFile(is);
		f.setId(key);
		f.save();
	}
	
	public InputStream retrieve(String key) {
		GridFS g = new GridFS(db,"binary");
		GridFSDBFile f = g.findOne(key);
		return f.getInputStream();
	}
}
