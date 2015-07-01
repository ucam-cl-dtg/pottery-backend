package uk.ac.cam.cl.dtg.teaching.pottery;

import java.io.File;
import java.io.InputStream;

import uk.ac.cam.cl.dtg.teaching.pottery.app.Config;

import com.google.inject.Inject;
import com.mongodb.DB;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.gridfs.GridFSInputFile;

public class BinaryManager {

	private File binaryRoot;
	
	private DB db;
	
	@Inject
	public BinaryManager(Database database, Config config) {
		this.db = database.getDb();
		binaryRoot = config.getBinaryRoot();
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
