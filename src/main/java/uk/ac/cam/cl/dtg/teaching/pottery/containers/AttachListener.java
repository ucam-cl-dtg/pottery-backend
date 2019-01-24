/*
 * pottery-backend - Backend API for testing programming exercises
 * Copyright © 2015-2018 BlueOptima Limited, Andrew Rice (acr31@cam.ac.uk)
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

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;

class AttachListener implements WebSocketListener {

  private final StringBuilder output = new StringBuilder();
  private boolean closed = false;
  private final int outputLimitChars;

  AttachListener(int outputLimitChars) {
    this.outputLimitChars = outputLimitChars;
  }

  public String getOutput() {
    return output.toString();
  }

  @Override
  public synchronized void onWebSocketClose(int statusCode, String reason) {
    notifyClose();
  }

  @Override
  public void onWebSocketConnect(Session session) {
    session.setIdleTimeout(0);
  }

  @Override
  public synchronized void onWebSocketError(Throwable cause) {
    notifyClose();
    throw new RuntimeException("WebSocket error attaching to container", cause);
  }

  @Override
  public void onWebSocketBinary(byte[] payload, int offset, int len) {
    output.append(new String(payload, offset, len));
    checkLength();
  }

  @Override
  public void onWebSocketText(String message) {
    output.append(message);
    checkLength();
  }

  synchronized boolean waitForClose(long timeoutMs) {
    long startTime = System.currentTimeMillis();
    try {
      while (!this.closed) {
        this.wait(timeoutMs);
        if (System.currentTimeMillis() - startTime >= timeoutMs) {
          return false;
        }
      }
      return true;
    } catch (InterruptedException e) {
      return false;
    }
  }

  synchronized void notifyClose() {
    closed = true;
    this.notifyAll();
  }

  boolean hasOverflowed() {
    return output.length() > outputLimitChars;
  }

  private void checkLength() {
    if (hasOverflowed()) {
      notifyClose();
    }
  }
}
