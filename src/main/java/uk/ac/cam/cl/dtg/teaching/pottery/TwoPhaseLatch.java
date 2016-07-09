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
