package poke.server.managers;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimeoutManager implements Runnable {
	protected static Logger logger = LoggerFactory.getLogger("timeoutmanager");
	private boolean DEBUG = true;
	protected static AtomicReference<TimeoutManager> instance = new AtomicReference<TimeoutManager>();
	private static int max;
    private static int min;
    private static Random rand;
    private static int randomNum;
    private static boolean isRunning;
    private int countDown;
    private Timer timer = new Timer();
	private int firstTime = 1;
	
	public static TimeoutManager initialize() {
		logger.info("TimeoutManager Initialized");
		instance.compareAndSet(null, new TimeoutManager());
		max=30;
		min=20;
		rand = new Random();
		randomNum = rand.nextInt((max - min) + 1) + min;
		isRunning=false;
		return instance.get();
	}
	
	public void reInitialize() {
		//logger.info("TimeoutManager reinitialied");
		rand = new Random();
		randomNum = rand.nextInt((max - min) + 1) + min;
	}
	
	public void reInitializeByHB() {
		logger.info("TimeoutManager reinitialied by HB");
		rand = new Random();
		randomNum = rand.nextInt((max - min) + 1) + min;
	}
	
	public static TimeoutManager getInstance() {
		// TODO throw exception if not initialized!
		return instance.get();
	}
	
	public void run () {
		
		 try{
			 while(true) { //This loop needs to go around try/catch - need to understand catch!
				 if(!isRunning) {
					 isRunning=true;
					 try {
						 timer.scheduleAtFixedRate(new TimerTask() {
					         //int i = randomNum;
					           
					           // if(!isRunning){
					            public void run() {
					                //logger.info("Counting down: "+randomNum);
					                randomNum--;
					                if (randomNum< 0)
					                {
					                	//timer.cancel();
					                	reInitialize();
					                    //If we reach here then we should start a new election.
					   				 	//logger.info("Timer reached 0 - need to start an election!");
					   				 	if(!ElectionManager.getInstance().isLeaderAlive())
					                    { 
					   				 		ElectionManager.getInstance().assessCurrentState();
					                    }
					   				 
					   				 	//If we reach here a second time before election is over than what do we do??
					   				 	isRunning=false;
					                    return;
					                }
					            }
 
							 
						 }, 0, 1000);
					 } catch(Exception e) {
						 System.out.println("Exception caught:"+e.getMessage());
					 }
					 
				 }
			 }
             
        }
        catch(Exception e)
        {
        	System.out.println("Exception caught:"+e.getMessage());
        } //End of catch block!
	} //End of run method

}
