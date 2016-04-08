package uk.ac.cam.cl.dtg.teaching.pottery;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class UUIDGenerator {

	private Set<String> allocated;
	
	public UUIDGenerator() {
		allocated = new HashSet<>();
	}
	
	public synchronized void reserve(String uuid) {
		allocated.add(uuid);
	}
	
	public synchronized void reserveAll(Collection<String> uuids) {
		allocated.addAll(uuids);
	}
	
	public synchronized String generate() {
		while(true) {
			String result = UUID.randomUUID().toString();
			if (!allocated.contains(result)) {
				allocated.add(result);
				return result;
			}
		}
	}
}
