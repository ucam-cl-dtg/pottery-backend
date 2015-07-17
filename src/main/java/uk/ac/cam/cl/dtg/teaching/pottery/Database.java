package uk.ac.cam.cl.dtg.teaching.pottery;

import java.net.UnknownHostException;

import org.mongojack.JacksonDBCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Singleton;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;

@Singleton
public class Database {
	
	private DB db;
	
	private static final Logger log = LoggerFactory.getLogger(Database.class);

	public Database() {
		try {
			db = new MongoClient("localhost").getDB("ptest");
		} catch (UnknownHostException e) {
			log.error("Failed to open database",e);
		}
	}
	
	public DB getDb() { return db; }

	public <T> JacksonDBCollection<T, String> getCollection(Class<T> clazz, String name) {
		DBCollection collection = db.getCollection(name);
		return JacksonDBCollection.wrap(collection, clazz, String.class);
	}

	public <T> JacksonDBCollection<T, String> getCollection(Class<T> clazz) {
		DBCollection collection = db.getCollection(clazz.getName());
		return JacksonDBCollection.wrap(collection, clazz, String.class);
	}

	
}
