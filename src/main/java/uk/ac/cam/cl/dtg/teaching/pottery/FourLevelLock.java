/**
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

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Lock class for protecting git repos with working directories.
 * 
 * Four levels of lock are provided:
 * 
 * 1) FullExclusion - this is for when you need to delete the git database or
 * the whole directoru. 
 * 
 * 2) GitDbOperation - this is when you are operating only on the git database
 * (not the files in the working directory). 
 * 
 * 3) FileReading - for reading files from the working directory
 * 
 * 4) FileWriting - for writing files in the working directory
 * 
 * This table shows who is allowed to run. If you hold the lock in the column then 
 * rows with an X are permitted to run as well.
 * 
 *                 | FullExclusion  | GitDbOperation | FileReading | FileWriting
 * ----------------+----------------+----------------+-------------+-------------
 * FullExclusion   |                |                |             |
 * GitDbOperation  |                |       X        |     X       |     X
 * FileReading     |                |       X        |     X       |
 * FileWriting     |                |       X        |             |      
 * @author acr31
 *
 */
public class FourLevelLock {

	public class AutoCloseableLock implements AutoCloseable {
		
		private Lock l1;
		private Lock l2;
		
		private AutoCloseableLock(Lock l1, Lock l2) throws InterruptedException {
			this.l1 = l1;
			this.l2 = l2;
			l1.lockInterruptibly();
			if (l2 != null) {
				try {
					l2.lockInterruptibly();
				}
				catch (InterruptedException e) {
					l1.unlock();
					throw e;
				}
			}
		}

		@Override
		public void close() {
			if (l2 != null) l2.unlock();
			l1.unlock();
		}
	}
	
	private final ReentrantReadWriteLock topLevelLock = new ReentrantReadWriteLock();
	
	private final ReentrantReadWriteLock secondLevelLock = new ReentrantReadWriteLock();
	
	public AutoCloseableLock takeFullExclusionLock() throws InterruptedException {
		return new AutoCloseableLock(
				topLevelLock.writeLock(), 
				secondLevelLock.writeLock());
	}
	
	public AutoCloseableLock takeGitDbOpLock() throws InterruptedException {
		return new AutoCloseableLock(
				topLevelLock.readLock(),
				null);
	}
	
	public AutoCloseableLock takeFileReadingLock() throws InterruptedException {
		return new AutoCloseableLock(
				topLevelLock.readLock(),
				secondLevelLock.readLock());
	}
	
	public AutoCloseableLock takeFileWritingLock() throws InterruptedException {
		return new AutoCloseableLock(
				topLevelLock.readLock(),
				secondLevelLock.writeLock());
	}
}
