package ibm.ANACONDA.Core;

import java.util.LinkedList;

public class ThreadPoolFramework {

	private final int nThreads;
	private final WorkingThread[] threads;
	private final LinkedList<MyRunnable> queue;

	public ThreadPoolFramework(int nThreads, LinkedList<MyRunnable> queue) {
		this(nThreads, queue, Thread.NORM_PRIORITY);
	}

	public ThreadPoolFramework(int nThreads, LinkedList<MyRunnable> queue, int threadPriority) {
		this.nThreads = nThreads;
		this.queue = queue;

		threads = new WorkingThread[nThreads];
		for (int i = 0; i < nThreads; i++) {
			threads[i] = new WorkingThread(i, nThreads);
			threads[i].setPriority(threadPriority);
			threads[i].start();
		}
	}

	public void Join() throws Exception {
		try {
			for (int i = 0; i < nThreads; i++) {
				if (Thread.currentThread().isInterrupted() == true) throw new InterruptedException();
				threads[i].join();
			}
		} catch (InterruptedException e) {
			for (int i = 0; i < nThreads; i++) {
				threads[i].interrupt();
			}
			throw new InterruptedException();
		}
	}

	private class WorkingThread extends Thread {
		private int threadID;
		private int numThread;
		private boolean finished = false;
		private boolean success = true;

		public WorkingThread(int threadID, int numThread) {
			this.threadID = threadID;
			this.numThread = numThread;
		}

		public void run() {
			try {
				while (true) {
					if (isInterrupted()) return;

					MyRunnable r;
					synchronized (queue) {
						if (queue.isEmpty()) break;
						r = queue.removeFirst();
					}

					if (r instanceof MyRunnableWithThreadID) {
						MyRunnableWithThreadID rr = (MyRunnableWithThreadID) r;
						rr.setTheadID(threadID, numThread);
					}

					r.run();
				}
			} catch (Throwable e) {
				e.printStackTrace();
				success = false;
			}
			finished = true;
		}
	}

	public boolean isAllSucceeded() {
		for (WorkingThread thread : threads) {
			if (thread.success == false) return false;
		}
		return true;
	}

	public boolean isAllFinished() {
		for (WorkingThread thread : threads) {
			if (thread.finished == false) return false;
		}
		return true;
	}
}
