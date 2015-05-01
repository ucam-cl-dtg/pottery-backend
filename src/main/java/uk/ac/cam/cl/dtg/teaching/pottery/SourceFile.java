package uk.ac.cam.cl.dtg.teaching.pottery;

import java.io.File;

public class SourceFile {
	File location;
	byte[] contents;
	String mimeType;
	
	public File getLocation() {
		return location;
	}
	public void setLocation(File location) {
		this.location = location;
	}
	public byte[] getContents() {
		return contents;
	}
	public void setContents(byte[] contents) {
		this.contents = contents;
	}
	public String getMimeType() {
		return mimeType;
	}
	public void setMimeType(String mimeType) {
		this.mimeType = mimeType;
	}
}