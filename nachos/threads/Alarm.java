package nachos.threads;  
import java.util.LinkedList;  
import nachos.machine.*;

  
  
public class Alarm {  
    public Alarm() {  
    Machine.timer().setInterruptHandler(new Runnable() {  
        public void run() { timerInterrupt(); }  
        });  
    }  
    
    public void timerInterrupt() {  
        boolean status = Machine.interrupt().disable();  
          
        KThread thread;  
        long currentTime = Machine.timer().getTime();//当前时间  
        int size=waitQueue.size();//得到等待队列的长度  
        //如果等待队列为空，return  
        if(size==0)  
            return;  
        //如果等待队列不为空，则将应该唤醒的线程唤醒  
        else{  
            for(int i=0;i<size;i++){  
                thread=waitQueue.get(i);  
                //如果索引为i的线程不应该被唤醒，那么其后的线程也不应该被唤醒  
                if(thread.wakeTime>currentTime)  
                    break;  
                else{  
                    thread.ready();  
                  System.out.println("实际被唤醒时间："+currentTime);  
                    waitQueue.remove(thread);  
                    size--;  
                    i--;                      
                }  
            }  
        }  
        Machine.interrupt().restore(status);  
    }  
    public void waitUntil(long x) {  
        boolean status = Machine.interrupt().disable();  
        long wakeTime = Machine.timer().getTime() + x;//线程应该被唤醒的时间  
      System.out.println("理论被唤醒时间："+wakeTime);  
        KThread.currentThread().wakeTime=wakeTime;//设置当前线程的wakeTime属性  
        int size=waitQueue.size();//等待队列长度  
        //将当前线程放入等待队列中，队列为有序队列，应放入相应位置  
        if(size==0)  
            waitQueue.add(KThread.currentThread());  
        else{  
            for(int i=0;i<size;i++){  
                if(wakeTime<waitQueue.get(i).wakeTime){  
                    waitQueue.add(i, KThread.currentThread());  
                    break;  
                }  
                if(i==size-1)  
                    waitQueue.add(size, KThread.currentThread());  
            }  
        }  
        KThread.currentThread().sleep();//将当前线程挂起  
        Machine.interrupt().restore(status);  
    }  
    LinkedList<KThread> waitQueue = new LinkedList();//有序的等待队列，存放的为调用了waitUntil方法的线程，前面的线程应该被唤醒的时间早  
 
}