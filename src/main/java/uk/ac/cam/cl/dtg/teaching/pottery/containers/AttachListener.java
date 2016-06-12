package uk.ac.cam.cl.dtg.teaching.pottery.containers;

import java.io.IOException;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;

class AttachListener implements WebSocketListener {
	private StringBuffer output;
	private String data;
	
	public AttachListener(StringBuffer output, String data) {
		this.output = output;
		this.data = data;
	}

	@Override
	public void onWebSocketClose(int statusCode, String reason) {			
	}

	@Override
	public void onWebSocketConnect(Session session) {
		if (data != null) {
			try {
				session.getRemote().sendString(data);
			} catch (IOException e) {
				throw new RuntimeException("Failed to send input data to container",e);
			}
		}
	}

	@Override
	public void onWebSocketError(Throwable cause) {
		throw new RuntimeException("WebSocket error attaching to container",cause);
	}

	@Override
	public void onWebSocketBinary(byte[] payload, int offset, int len) {
		throw new RuntimeException("Unexpected binary data from container");
	}

	@Override
	public void onWebSocketText(String message) {
		output.append(message);
	}		
}