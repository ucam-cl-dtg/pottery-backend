package uk.ac.cam.cl.dtg.teaching.pottery.app;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;

import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.InvalidTagFormatException;

public class RegistrationTag {

	private static final String PREFIX_ACTIVE = "active-task-";
	private static final String PREFIX_DISABLED = "disabled-task-";
	
	private static final int PREFIX_ACTIVE_LENGTH = PREFIX_ACTIVE.length();
	private static final int PREFIX_DISABLED_LENGTH = PREFIX_DISABLED.length();
	private static final int R_TAGS_LENGTH = Constants.R_TAGS.length();

	private File repository;
	private boolean active;
	private String registeredTaskUUID;
	
	public RegistrationTag(boolean active, String registeredTaskUUID, File repo) {
		super();
		this.active = active;
		this.registeredTaskUUID = registeredTaskUUID;
		this.repository = repo;
	}

	public File getRepository() {
		return repository;
	}

	public boolean isActive() {
		return active;
	}

	public String getRegisteredTaskUUID() {
		return registeredTaskUUID;
	}

	public String toTagName() {
		return (active?PREFIX_ACTIVE:PREFIX_DISABLED)+registeredTaskUUID;
	}
	
	public static RegistrationTag fromTagName(String tagName, File repo) throws InvalidTagFormatException {
		if (tagName.startsWith(Constants.R_TAGS)) {
			tagName = tagName.substring(R_TAGS_LENGTH);
		}
		
		boolean active;
		String uuid;
		if (tagName.startsWith(PREFIX_ACTIVE)) {
			active = true;
			uuid = tagName.substring(PREFIX_ACTIVE_LENGTH);
		}
		else if (tagName.startsWith(PREFIX_DISABLED)) {
			active = false;
			uuid = tagName.substring(PREFIX_DISABLED_LENGTH);
		}
		else {
			throw new InvalidTagFormatException("Failed to parse tag "+tagName);
		}
		return new RegistrationTag(active, uuid,repo);
	}
	
	/**
	 * Lookup the SHA1 that corresponds to this tag
	 * @throws IOException 
	 */
	public String lookupSHA1() throws IOException {
		try(Git g = Git.open(repository)) {
			ObjectId o = g.getRepository().resolve(toTagName());
			return o.getName();
		}
	}
	
}
