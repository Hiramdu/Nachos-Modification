package nachos.threads;

import java.util.LinkedList;

import nachos.machine.*;

/**
 * A KThread is a thread that can be used to execute Nachos kernel code. Nachos
 * allows multiple threads to run concurrently.
 *
 * To create a new thread of execution, first declare a class that implements
 * the <tt>Runnable</tt> interface. That class then implements the <tt>run</tt>
 * method. An instance of the class can then be allocated, passed as an argument
 * when creating <tt>KThread</tt>, and forked. For example, a thread that
 * computes pi could be written as follows:
 *
 * <p>
 * <blockquote>
 * 
 * <pre>
 * class PiRun implements Runnable {
 *     public void run() {
 *         // compute pi
 *         ...
 *     }
 * }
 * </pre>
 * 
 * </blockquote>
 * <p>
 * The following code would then create a thread and start it running:
 *
 * <p>
 * <blockquote>
 * 
 * <pre>
 * PiRun p = new PiRun();
 * new KThread(p).fork();
 * </pre>
 * 
 * </blockquote>
 */
public class KThread {
	/**
	 * Get the current thread.
	 *
	 * @return the current thread.
	 */
	public static KThread currentThread() {
		Lib.assertTrue(currentThread != null);
		return currentThread;
	}

	/**
	 * Allocate a new <tt>KThread</tt>. If this is the first <tt>KThread</tt>,
	 * create an idle thread as well.
	 */
	public KThread() {
		if (currentThread != null) {
			tcb = new TCB();
		} else {
			readyQueue = ThreadedKernel.scheduler.newThreadQueue(false);
			readyQueue.acquire(this);

			currentThread = this;
			tcb = TCB.currentTCB();
			name = "main";
			restoreState();

			createIdleThread();
		}
	}

	/**
	 * Allocate a new KThread.
	 *
	 * @param target
	 *            the object whose <tt>run</tt> method is called.
	 */
	public KThread(Runnable target) {
		this();
		this.target = target;
	}

	/**
	 * Set the target of this thread.
	 *
	 * @param target
	 *            the object whose <tt>run</tt> method is called.
	 * @return this thread.
	 */
	public KThread setTarget(Runnable target) {
		Lib.assertTrue(status == statusNew);

		this.target = target;
		return this;
	}

	/**
	 * Set the name of this thread. This name is used for debugging purposes
	 * only.
	 *
	 * @param name
	 *            the name to give to this thread.
	 * @return this thread.
	 */
	public KThread setName(String name) {
		this.name = name;
		return this;
	}

	/**
	 * Get the name of this thread. This name is used for debugging purposes
	 * only.
	 *
	 * @return the name given to this thread.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Get the full name of this thread. This includes its name along with its
	 * numerical ID. This name is used for debugging purposes only.
	 *
	 * @return the full name given to this thread.
	 */
	public String toString() {
		return (name + " (#" + id + ")");
	}

	/**
	 * Deterministically and consistently compare this thread to another thread.
	 */
	public int compareTo(Object o) {
		KThread thread = (KThread) o;

		if (id < thread.id)
			return -1;
		else if (id > thread.id)
			return 1;
		else
			return 0;
	}

	/**
	 * Causes this thread to begin execution. The result is that two threads are
	 * running concurrently: the current thread (which returns from the call to
	 * the <tt>fork</tt> method) and the other thread (which executes its
	 * target's <tt>run</tt> method).
	 */
	public void fork() {
		Lib.assertTrue(status == statusNew);
		Lib.assertTrue(target != null);

		Lib.debug(dbgThread, "Forking thread: " + toString() + " Runnable: " + target);

		boolean intStatus = Machine.interrupt().disable();

		tcb.start(new Runnable() {
			public void run() {
				runThread();
			}
		});

		ready();

		Machine.interrupt().restore(intStatus);
	}

	private void runThread() {
		begin();
		target.run();
		finish();
	}

	private void begin() {
		Lib.debug(dbgThread, "Beginning thread: " + toString());

		Lib.assertTrue(this == currentThread);

		restoreState();

		Machine.interrupt().enable();
	}

	/**
	 * Finish the current thread and schedule it to be destroyed when it is safe
	 * to do so. This method is automatically called when a thread's
	 * <tt>run</tt> method returns, but it may also be called directly.
	 *
	 * The current thread cannot be immediately destroyed because its stack and
	 * other execution state are still in use. Instead, this thread will be
	 * destroyed automatically by the next thread to run, when it is safe to
	 * delete this thread.
	 */
	/*
	 * public static void finish() { Lib.debug(dbgThread, "Finishing thread: " +
	 * currentThread.toString());
	 * 
	 * Machine.interrupt().disable();
	 * 
	 * Machine.autoGrader().finishingCurrentThread();
	 * 
	 * Lib.assertTrue(toBeDestroyed == null); toBeDestroyed = currentThread;
	 * 
	 * 
	 * currentThread.status = statusFinished;
	 * 
	 * sleep(); }
	 */
	/**
	 * Relinquish the CPU if any other thread is ready to run. If so, put the
	 * current thread on the ready queue, so that it will eventually be
	 * rescheuled.
	 *
	 * <p>
	 * Returns immediately if no other thread is ready to run. Otherwise returns
	 * when the current thread is chosen to run again by
	 * <tt>readyQueue.nextThread()</tt>.
	 *
	 * <p>
	 * Interrupts are disabled, so that the current thread can atomically add
	 * itself to the ready queue and switch to the next thread. On return,
	 * restores interrupts to the previous state, in case <tt>yield()</tt> was
	 * called with interrupts disabled.
	 */
	public static void yield() {
		Lib.debug(dbgThread, "Yielding thread: " + currentThread.toString());

		Lib.assertTrue(currentThread.status == statusRunning);

		boolean intStatus = Machine.interrupt().disable();

		currentThread.ready();

		runNextThread();

		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Relinquish the CPU, because the current thread has either finished or it
	 * is blocked. This thread must be the current thread.
	 *
	 * <p>
	 * If the current thread is blocked (on a synchronization primitive, i.e. a
	 * <tt>Semaphore</tt>, <tt>Lock</tt>, or <tt>Condition</tt>), eventually
	 * some thread will wake this thread up, putting it back on the ready queue
	 * so that it can be rescheduled. Otherwise, <tt>finish()</tt> should have
	 * scheduled this thread to be destroyed by the next thread to run.
	 */
	public static void sleep() {
		Lib.debug(dbgThread, "Sleeping thread: " + currentThread.toString());

		Lib.assertTrue(Machine.interrupt().disabled());

		if (currentThread.status != statusFinished)
			currentThread.status = statusBlocked;

		runNextThread();
	}

	/**
	 * Moves this thread to the ready state and adds this to the scheduler's
	 * ready queue.
	 */
	public void ready() {
		Lib.debug(dbgThread, "Ready thread: " + toString());

		Lib.assertTrue(Machine.interrupt().disabled());
		Lib.assertTrue(status != statusReady);

		status = statusReady;
		if (this != idleThread)
			readyQueue.waitForAccess(this);

		Machine.autoGrader().readyThread(this);
	}

	/**
	 * Waits for this thread to finish. If this thread is already finished,
	 * return immediately. This method must only be called once; the second call
	 * is not guaranteed to return. This thread must not be the current thread.
	 */
	public void join() {// 线程B中有A.join()语句，则B等A执行完才能执行
		Lib.debug(dbgThread, "Joining to thread: " + toString());
		Lib.assertTrue(this != currentThread);
		Lib.assertTrue(join_counter == 0);
		join_counter++;
		boolean status = Machine.interrupt().disable();
		if (this.status != statusFinished) {
			
			
			waitQueue.waitForAccess(KThread.currentThread());
			
			currentThread.sleep();
			
		}
		Machine.interrupt().restore(status);
	}


	public static void finish() {
		Lib.debug(dbgThread, "Finishingthread:" + currentThread.toString());// 调试
		Machine.interrupt().disable();
		Machine.autoGrader().finishingCurrentThread();// 告诉TCB当前线程结束了
		Lib.assertTrue(toBeDestroyed == null);
		toBeDestroyed = currentThread;
		currentThread.status = statusFinished;// 当前线程状态置为结束
		KThread thread;
		while ((thread = currentThread().waitQueue.nextThread()) != null) {
			thread.ready();// 取出就绪队列所有线程
		}
		currentThread.joinSignal.V();
		sleep();// 将当前线程置为完成态，读取下一个就绪线程
	}

	/**
	 * Create the idle thread. Whenever there are no threads ready to be run,
	 * and <tt>runNextThread()</tt> is called, it will run the idle thread. The
	 * idle thread must never block, and it will only be allowed to run when all
	 * other threads are blocked.
	 *
	 * <p>
	 * Note that <tt>ready()</tt> never adds the idle thread to the ready set.
	 */
	private static void createIdleThread() {
		Lib.assertTrue(idleThread == null);

		idleThread = new KThread(new Runnable() {
			public void run() {
				while (true)
					yield();
			}
		});
		idleThread.setName("idle");

		Machine.autoGrader().setIdleThread(idleThread);

		idleThread.fork();
	}

	/**
	 * Determine the next thread to run, then dispatch the CPU to the thread
	 * using <tt>run()</tt>.
	 */
	private static void runNextThread() {
		KThread nextThread = readyQueue.nextThread();
		if (nextThread == null)
			nextThread = idleThread;

		nextThread.run();
	}

	/**
	 * Dispatch the CPU to this thread. Save the state of the current thread,
	 * switch to the new thread by calling <tt>TCB.contextSwitch()</tt>, and
	 * load the state of the new thread. The new thread becomes the current
	 * thread.
	 *
	 * <p>
	 * If the new thread and the old thread are the same, this method must still
	 * call <tt>saveState()</tt>, <tt>contextSwitch()</tt>, and
	 * <tt>restoreState()</tt>.
	 *
	 * <p>
	 * The state of the previously running thread must already have been changed
	 * from running to blocked or ready (depending on whether the thread is
	 * sleeping or yielding).
	 *
	 * @param finishing
	 *            <tt>true</tt> if the current thread is finished, and should be
	 *            destroyed by the new thread.
	 */
	private void run() {
		Lib.assertTrue(Machine.interrupt().disabled());

		Machine.yield();

		currentThread.saveState();

		Lib.debug(dbgThread, "Switching from: " + currentThread.toString() + " to: " + toString());

		currentThread = this;

		tcb.contextSwitch();

		currentThread.restoreState();
	}

	/**
	 * Prepare this thread to be run. Set <tt>status</tt> to
	 * <tt>statusRunning</tt> and check <tt>toBeDestroyed</tt>.
	 */
	protected void restoreState() {
		Lib.debug(dbgThread, "Running thread: " + currentThread.toString());

		Lib.assertTrue(Machine.interrupt().disabled());
		Lib.assertTrue(this == currentThread);
		Lib.assertTrue(tcb == TCB.currentTCB());

		Machine.autoGrader().runningThread(this);

		status = statusRunning;

		if (toBeDestroyed != null) {
			toBeDestroyed.tcb.destroy();
			toBeDestroyed.tcb = null;
			toBeDestroyed = null;
		}
	}

	/**
	 * Prepare this thread to give up the processor. Kernel threads do not need
	 * to do anything here.
	 */
	protected void saveState() {
		Lib.assertTrue(Machine.interrupt().disabled());
		Lib.assertTrue(this == currentThread);
	}

	private static class PingTest implements Runnable {
	    PingTest(int which) {
	        this.which = which;
	    }

	    public void run() {
	        for (int i=0; i<5; i++) {
	        System.out.println("*** thread " + which + " looped "
	                   + i + " times");
	        KThread.currentThread().yield();
	        }
	    }

	    private int which;
	    }
	public static void PriorityTest(){
	    boolean status = Machine.interrupt().disable();//关中断，setPriority()函数中要求关中断
	    final KThread a = new KThread(new PingTest(1)).setName("thread1");
	    new PriorityScheduler().setPriority(a,2);
	    System.out.println("thread1的优先级为："+new PriorityScheduler().getThreadState(a).priority);
	    KThread b = new KThread(new PingTest(2)).setName("thread2");
	    new PriorityScheduler().setPriority(b,4);
	    System.out.println("thread2的优先级为："+new PriorityScheduler().getThreadState(b).priority);
	    KThread c = new KThread(new Runnable(){
	        public void run(){
	            for (int i=0; i<5; i++) {
	                if(i==2) 
	                    b.join();
	                System.out.println("*** thread 3 looped "
	                           + i + " times");
	                KThread.currentThread().yield();
	            }
	        }
	    }).setName("thread3");
	    new PriorityScheduler().setPriority(c,6);
	    System.out.println("thread3的优先级为："+new PriorityScheduler().getThreadState(c).priority);
	    a.fork();
	    b.fork();
	    c.fork();
	    Machine.interrupt().restore(status);
	}

	private static class Speaker implements Runnable {
		private Communicator c;

		Speaker(Communicator c) {
			this.c = c;
		}

		public void run() {
			for (int i = 0; i < 5; ++i) {
				System.out.println("speaker speaking" + i);
				c.speak(i);
				System.out.println("speaker spoken");
				KThread.yield();
			}
		}
	}

	public static void SpeakTest() {
		System.out.println("测试Communicator类：");
		Communicator c = new Communicator();
		new KThread(new Speaker(c)).setName("Speaker").fork();
		for (int i = 0; i < 5; ++i) {
			System.out.println("listener listening " + i);
			int x = c.listen();
			System.out.println("listener listened, word = " + x);
			KThread.yield();
		}
	}

	/**
	 * Tests whether this module is working.
	 */
	public static void selfTest() {
		Lib.debug(dbgThread, "Enter KThread.selfTest");
		// CommunicatorTest test=new CommunicatorTest();//task1.4
		// ConditionTest test=new ConditionTest();//task1.2

		//new KThread(new PingTest(1)).setName("forked thread").fork();
		//new PingTest(0).run();
		KThread.joinTest();// task1.1
		// KThread.alarmTest();//task1.3
		
		 //KThread.PriorityTest();//task1.5
		// Boat test = new Boat();//task1.6
		// test.selfTest();//task1.6
	ThreadTest.doLotterySchedulerTest();//task2.4
	}

	private static final char dbgThread = 't';

	/**
	 * Additional state used by schedulers.
	 *
	 * @see nachos.threads.PriorityScheduler.ThreadState
	 */
	public Object schedulingState = null;

	private static final int statusNew = 0;
	private static final int statusReady = 1;
	private static final int statusRunning = 2;
	private static final int statusBlocked = 3;
	private static final int statusFinished = 4;

	/**
	 * The status of this thread. A thread can either be new (not yet forked),
	 * ready (on the ready queue but not running), running, or blocked (not on
	 * the ready queue and not running).
	 */
	private int status = statusNew;
	private String name = "(unnamed thread)";
	private Runnable target;
	private TCB tcb;

	/**
	 * Unique identifer for this thread. Used to deterministically compare
	 * threads.
	 */
	private int id = numCreated++;

	public long wakeTime = 0;// 线程的等待时间，初始化为0
	/** Number of times the KThread constructor was called. */
	private static int numCreated = 0;
	private int join_counter = 0;

	private static ThreadQueue readyQueue = null;
	private static KThread currentThread = null;
	private static KThread toBeDestroyed = null;
	private static KThread idleThread = null;
	private static ThreadQueue waitQueue = ThreadedKernel.scheduler.newThreadQueue(true);
	private Semaphore joinSignal = new Semaphore(0);

	Lock lock;
	private int speakerNum;
	private int listenerNum;
	private LinkedList<Integer> words;
	Condition2 listener;
	Condition2 speaker;

	public static void joinTest() {
		System.out.println("This is join test:");
		Lib.debug(dbgThread, "Enter KThread.selfTest");

		KThread a = new KThread(new PingTest(1)).setName("forked thread");
		a.fork();
		a.join();

		new PingTest(0).run();
	}

	private static void alarmTest() {
		new KThread(new Runnable() {
			public void run() {
				System.out.println("当前时间：" + Machine.timer().getTime());
				ThreadedKernel.alarm.waitUntil(500);
				System.out.println("当前时间：" + Machine.timer().getTime());
			}
		}).fork();
	}

	

	
	
	public static void doLotterySchedulerTest() {
		final KThread thread1 = new KThread(new Runnable() {
			public void run() {
				for (int i = 0; i < 3; i++) {
					KThread.currentThread().yield();
					System.out.println("thread1");
				}

			}
		});
		KThread thread2 = new KThread(new Runnable() {
			public void run() {

				for (int i = 0; i < 3; i++) {
					KThread.currentThread().yield();
					System.out.println("thread2");
				}

			}
		});
		KThread thread3 = new KThread(new Runnable() {
			public void run() {

				thread1.join();

				for (int i = 0; i < 3; i++) {
					KThread.currentThread().yield();
					System.out.println("thread3");
				}
			}
		});
		boolean status = Machine.interrupt().disable();
		ThreadedKernel.scheduler.setPriority(thread1, 1);
		ThreadedKernel.scheduler.setPriority(thread2, 2);
		ThreadedKernel.scheduler.setPriority(thread3, 7);
		thread1.setName("thread111");

		thread2.setName("thread2222");

		thread3.setName("thread33333");

		Machine.interrupt().restore(status);
		thread1.fork();
		thread2.fork();
		thread3.fork();
	}

}