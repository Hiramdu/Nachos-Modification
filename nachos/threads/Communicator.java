package nachos.threads;
import java.util.LinkedList;
import java.util.Queue;
import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {
	/**
	 * Allocate a new communicator.
	 */
	// private SynchList Speaker;
	// private SynchList Listener;
	public Communicator() {

		lock = new Lock();
		queue = new LinkedList<Integer>();// 保存说者话语的队列
		speaker = new Condition2(lock);
		listener = new Condition2(lock);
		word = 0;
		speakercount = 0;
		listenercount = 0;
	}

	public void speak(int word) {
		// 先获得锁，然后进行判断，如果没有听者等待，就要把说者放入队列然后睡眠。如果有听者等待，就要唤醒一个听者，然后传递消息，最后释放锁。
		boolean intStatus = Machine.interrupt().disable();

		lock.acquire();
		if (listenercount == 0) {
			speakercount++;
			queue.offer(word);
			speaker.sleep();
			listener.wake();// 尝试唤醒听者
			speakercount--;
		} else {
			queue.offer(word);
			listener.wake();
		}
		lock.release();
		Machine.interrupt().restore(intStatus);
		return;
	}

	public int listen() {
		// 先获得锁，然后进行判断尝试唤醒speaker，如果没有说者等待，就要把听者放入队列然后睡眠。如果有说者等待，就要唤醒一个说者，将自己挂起以等待speaker准备好数据再将自己唤醒，然后传递消息，最后释放锁。
		boolean intStatus = Machine.interrupt().disable();
		lock.acquire();
		if (speakercount != 0) {
			speaker.wake();
			listener.sleep();
		}

		else {
			listenercount++;
			listener.sleep();
			listenercount--;
		}
		lock.release();
		Machine.interrupt().restore(intStatus);
		return queue.poll();
	}

	private Lock lock;
	private Condition2 speaker, listener;
	private int word, speakercount, listenercount;
	private Queue<Integer> queue;

}
