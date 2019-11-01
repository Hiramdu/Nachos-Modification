package nachos.threads;

import nachos.machine.Machine;

public class ThreadTest {
	private static int a = 1, b = 2, c = 3, d = 0;
	
	public static void doJoinTest() {
		KThread kt1 = new KThread(new Runnable() {
			public void run() {
				for(int i = 0; i < 10; i++) {
					System.out.println("thread 1 running " + i + " times");
					if(i == 5) {
						KThread kt2 = new KThread(new JoinTest());
						kt2.fork();
						kt2.join();
					}
				}
			}
		});
		kt1.fork();
	}
	
	public static class JoinTest implements Runnable {
		public void run() {
			for(int i = 0; i < 5; i++) {
				System.out.println("thread 2 join and running!");
			}
		}
	}
	
	
	public static void doConditionTest() {
		Lock lock = new Lock();
		Condition2 condition = new Condition2(lock);
		new KThread(new ConditionTest1(condition, lock)).fork();
		new ConditionTest2(condition, lock).run();
	}
	
	public static class ConditionTest1 implements Runnable {
		private Condition2 condition;
		private Lock lock;
		ConditionTest1(Condition2 condition, Lock lock) {
			this.condition = condition;
			this.lock = lock;
		}
		public void run() {
			a = b + c;
			System.out.println("thread 1 is calculating a = b + c = " + a);
			System.out.println("thread 1 has finished calculating and will wake up thread 2!");
			lock.acquire();
			condition.wake();
			lock.release();
		}
	}
	
	public static class ConditionTest2 implements Runnable {
		private Condition2 condition;
		private Lock lock;
		ConditionTest2(Condition2 condition, Lock lock) {
			this.condition = condition;
			this.lock = lock;
		}
		public void run() {
			lock.acquire();
			System.out.println("thread 2 will sleep!");
			condition.sleep();
			d = a + c;
			System.out.println("thread 2 is calculating d = a + c = " + d);
			System.out.println("thread 2 has finished calculating!");
			lock.release();
		}
	}
	
	public static void doWaitUntilTest() {
		new KThread(new WaitUntilTest()).fork();
	}
	
	public static class WaitUntilTest implements Runnable {
		public void run() {
			Alarm alarm = new Alarm();
			int waitTime = 440;
			System.out.println("call waitUntil(" + waitTime + ") at " + Machine.timer().getTime());
			alarm.waitUntil(waitTime);
			System.out.println("thread waken at " + Machine.timer().getTime());
		}
	}
	
	public static void doCommunicatorTest() {
		Communicator communicator = new Communicator();
		new KThread(new CommunicatorSpeak(communicator, 123456)).setName("speaker thread").fork();
		new KThread(new CommunicatorListen(communicator)).setName("listener thread").fork();
	}
	
	public static class CommunicatorSpeak implements Runnable {
		CommunicatorSpeak(Communicator communicator, int word) {
			this.communicator = communicator;
			this.word = word;
		}
		public void run() {
			communicator.speak(word);
		}
		private Communicator communicator;
		private int word;
	}

	public static class CommunicatorListen implements Runnable {
		CommunicatorListen(Communicator communicator) {
			this.communicator = communicator;
		}
		public void run() {
			communicator.listen();
		}
		private Communicator communicator;
	}
	
	public static void doPrioritySchedulerTest() {
		
		final KThread thread1 = new KThread(new Runnable() {
			public void run() {
				for(int i=0;i<3;i++) {
					KThread.currentThread().yield();
					System.out.println("thread1");
				}
				
			}
		});
		KThread thread2 = new KThread(new Runnable() {
			public void run() {
				for(int i=0;i<3;i++) {
					KThread.currentThread().yield();
					System.out.println("thread2");
				}
				
			}
		});
		KThread thread3 = new KThread(new Runnable() {
			public void run() {
				thread1.join();
				
                for(int i=0;i<3;i++) {
                	KThread.currentThread().yield();
    				System.out.println("thread3");
                }
			}
		});
		boolean status = Machine.interrupt().disable();
		ThreadedKernel.scheduler.setPriority(thread1, 2);
		ThreadedKernel.scheduler.setPriority(thread2, 4);
		ThreadedKernel.scheduler.setPriority(thread3, 6);
		thread1.setName("thread1");
		thread2.setName("thread2");
		thread3.setName("thread3");
		Machine.interrupt().restore(status);
		thread1.fork();
        thread2.fork();
		thread3.fork();
		
		
		
	}
	
	public static class PriorityTest implements Runnable {
		public void run() {
			for(int i = 0; i < 7; i++) {
				System.out.println("thread " + KThread.currentThread().getName() + " is running priority ");
				if(i == 4 && KThread.currentThread().getName().equals("kt4")) {
					KThread k = new KThread(new Runnable() {
						public void run() {
							System.out.println(KThread.currentThread().getName() + " running Priority: ");
						}
					});
					k.setName("k");
					k.fork();
					k.join();
				}
 			}
		}
	}
	
	public static void boatTest() {
		Boat.selfTest();
	}
	
	public static void doLotterySchedulerTest() {
		final KThread thread1 = new KThread(new Runnable() {
			public void run() {
				for(int i=0;i<3;i++)
				   {KThread.currentThread().yield();
					System.out.println("thread1");}
				
			}
		});
		KThread thread2 = new KThread(new Runnable() {
			public void run() {

				for(int i=0;i<3;i++)
			   {KThread.currentThread().yield();
				System.out.println("thread2");}
				
			}
		});
		KThread thread3 = new KThread(new Runnable() {
			public void run() {
				
               // thread1.join();
				
                for(int i=0;i<3;i++)
				{KThread.currentThread().yield();
				System.out.println("thread3");}
				}
		});
		boolean status = Machine.interrupt().disable();
		ThreadedKernel.scheduler.setPriority(thread1, 7);
		ThreadedKernel.scheduler.setPriority(thread2, 2);
		ThreadedKernel.scheduler.setPriority(thread3, 1);
		thread1.setName("thread1");
		
		thread2.setName("thread2");
		
		thread3.setName("thread3");
		
		Machine.interrupt().restore(status);
		thread1.fork();
        thread2.fork();
		thread3.fork();
	}
	
}
