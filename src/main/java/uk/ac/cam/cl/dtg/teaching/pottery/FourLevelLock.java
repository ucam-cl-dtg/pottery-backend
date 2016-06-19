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
