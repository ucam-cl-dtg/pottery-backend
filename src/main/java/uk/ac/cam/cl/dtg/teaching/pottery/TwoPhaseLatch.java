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
package uk.ac.cam.cl.dtg.teaching.pottery;

/**
 * A two phase countdown latch. Threads can concurrently acquire and release the
 * latch until someone calls await(). After await is called new threads are
 * refused at the acquire point. Await returns after all threads release()
 * 
 * @author acr31
 *
 */
public class TwoPhaseLatch {
	
	private int counter = 0;
	private boolean running = true;
	
	
	/**
	 * Acquire access. If access is granted you must call release at some point later
	 * 
	 * @return true if permitted or false if not.
	 */
	public synchronized boolean acquire() {
		if (!running) return false;
		counter++;
		return true;
	}
	
	/**
	 * Release the resource.
	 */
	public synchronized void release() {
		counter--;
		notifyAll();
	}
	
	/**
	 * Prevent new threads acquiring the resource and wait for current ones to release
	 * @throws InterruptedException 
	 */
	public synchronized void await() throws InterruptedException {
		running = false;
		while(counter > 0) {
			wait();
		}
	}

}
