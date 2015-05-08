package uk.ac.cam.cl.dtg.teaching.pottery.dto;

import javax.ws.rs.FormParam;

import org.jboss.resteasy.annotations.providers.multipart.PartType;

public class FileData {
	
	@FormParam("mimeType")
	private String mimeType;
	
	@FormParam("data")
	@PartType("application/octet-stream") byte[] data;
	
	public String getMimeType() {
		return mimeType;
	}
	public void setMimeType(String mimeType) {
		this.mimeType = mimeType;
	}
	public byte[] getData() {
		return data;
	}
	public void setData(byte[] data) {
		this.data = data;
	}
}