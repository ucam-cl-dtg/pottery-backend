/*
 * pottery-backend - Backend API for testing programming exercises
 * Copyright © 2015 Andrew Rice (acr31@cam.ac.uk)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package uk.ac.cam.cl.dtg.teaching.pottery.containers;

import java.io.IOException;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;

class AttachListener implements WebSocketListener {
	private StringBuffer output;
	private String data;
	
	private boolean closed = false;
	
	public AttachListener(StringBuffer output, String data) {
		this.output = output;
		this.data = data;
	}

	@Override
	public synchronized void onWebSocketClose(int statusCode, String reason) {
		closed = true;
		this.notifyAll();
	}

	@Override
	public void onWebSocketConnect(Session session) {
		if (data != null) {
			try {
				StringBuffer toSend = new StringBuffer(data);
				// Push a load of newlines down the pipe at the end to make sure that
				// we don't get blocked on line buffering
				for(int i=0;i<10;++i) toSend.append(System.lineSeparator());
				session.getRemote().sendString(toSend.toString());
			} catch (IOException e) {
				throw new RuntimeException("Failed to send input data to container",e);
			}
		}
	}

	@Override
	public synchronized void onWebSocketError(Throwable cause) {
		closed = true;
		this.notifyAll();
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
	
	public synchronized boolean waitForClose(long timeoutMs) throws InterruptedException {
		long startTime = System.currentTimeMillis();
		while(!this.closed) {
			this.wait(timeoutMs);
			if (System.currentTimeMillis() - startTime >= timeoutMs) { return false; }
		}
		return true;
	}

	public synchronized void notifyClose() {
		closed = true;
		this.notifyAll();
	}
}