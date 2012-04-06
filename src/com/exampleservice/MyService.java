package com.exampleservice;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ConsumerCancelledException;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.ShutdownSignalException;


public class MyService extends Service {
	private String QUEUE_NAME = "sensorHR";
	 private static final String EXCHANGE_NAME = "topic_logs";
	private String HOST = "192.168.1.84";
	private ArrayList<String> arr = new ArrayList<String>();
	private ConnectionFactory factory = new ConnectionFactory();
	
	private Connection connection;
	private Channel channel;
	private QueueingConsumer consumer = null;
	private String message = "ZERO";
	
    private NotificationManager nm;
    private Timer timer = new Timer();
    private int counter = 0, incrementby = 1;
    private static boolean isRunning = false;

    ArrayList<Messenger> mClients = new ArrayList<Messenger>(); // Keeps track of all current registered clients.
    int mValue = 0; // Holds last value set by a client.
    static final int MSG_REGISTER_CLIENT = 1;
    static final int MSG_UNREGISTER_CLIENT = 2;
    static final int MSG_SET_INT_VALUE = 3;
    static final int MSG_SET_STRING_VALUE = 4;
    final Messenger mMessenger = new Messenger(new IncomingHandler()); // Target we publish for clients to send messages to IncomingHandler.


    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }
    class IncomingHandler extends Handler { // Handler of incoming messages from clients.
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_REGISTER_CLIENT:
                mClients.add(msg.replyTo);
                break;
            case MSG_UNREGISTER_CLIENT:
                mClients.remove(msg.replyTo);
                break;
            case MSG_SET_INT_VALUE:
                incrementby = msg.arg1;
                break;
            default:
                super.handleMessage(msg);
            }
        }
    }
    private void sendMessageToUI(int intvaluetosend, String message) {
        for (int i=mClients.size()-1; i>=0; i--) {
            try {
                // Send data as an Integer
                mClients.get(i).send(Message.obtain(null, MSG_SET_INT_VALUE, intvaluetosend, 0));

                //Send data as a String
                Bundle b = new Bundle();
                b.putString("str1", message);
                Message msg = Message.obtain(null, MSG_SET_STRING_VALUE);
                msg.setData(b);
                mClients.get(i).send(msg);

            } catch (RemoteException e) {
                // The client is dead. Remove it from the list; we are going through the list from back to front so this is safe to do inside the loop.
                mClients.remove(i);
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        factory = new ConnectionFactory();
        Log.i("CONNECTION", QUEUE_NAME + ":" + HOST);
        
        try {
        	factory.setHost(HOST);
        	 Log.i("HOST", factory.getHost());
			connection = factory.newConnection();
			 Log.i("CONN", connection.getAddress().toString());
			 channel = connection.createChannel();
			 channel.queueDeclare(QUEUE_NAME, false, false, false, null);
			 consumer = new QueueingConsumer(channel);
			 
			 
		} catch (IOException e) {
			Log.i("IOEXCEPTION", "onCreate()");
			e.printStackTrace();
		}

        Log.i("MyService", "Service Started.");
        
        showNotification();
        timer.scheduleAtFixedRate(new TimerTask(){ public void run() {onTimerTick();}}, 0, 1000);
        isRunning = true;
    }
    private void showNotification() {
        nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence text = getText(R.string.service_started);
        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(R.drawable.ic_launcher, text, System.currentTimeMillis());
        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getText(R.string.service_label), text, contentIntent);
        // Send the notification.
        // We use a layout id because it is a unique number.  We use it later to cancel.
        nm.notify(R.string.service_started, notification);
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("MyService", "Received start id " + startId + ": " + intent);
        return START_STICKY; // run until explicitly stopped.
    }

    public static boolean isRunning()
    {
        return isRunning;
    }


    private void onTimerTick() {
        
    	try {
    	      ConnectionFactory factory = new ConnectionFactory();
    	      factory.setHost("192.168.1.84");
    	  
    	      connection = factory.newConnection();
    	      channel = connection.createChannel();

    	      channel.exchangeDeclare(EXCHANGE_NAME, "topic");
    	      String queueName = channel.queueDeclare().getQueue();
    	 
//    	      if (argv.length < 1){
//    	        System.err.println("Usage: ReceiveLogsTopic [binding_key]...");
//    	        System.exit(1);
//    	      }
    	    
//    	      for(String bindingKey : argv){    
    	        channel.queueBind(queueName, EXCHANGE_NAME, "anonymous.info");
    	     // }
    	    
    	      System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

    	      QueueingConsumer consumer = new QueueingConsumer(channel);
    	      channel.basicConsume(queueName, true, consumer);

    	    
    	        QueueingConsumer.Delivery delivery = consumer.nextDelivery();
    	        String message = new String(delivery.getBody());
    	        String routingKey = delivery.getEnvelope().getRoutingKey();

    	        try {
    	            counter += incrementby;
    	       
    	    		 sendMessageToUI(counter, message + ":" + routingKey);
    	    	
    	        } catch (Throwable t) { //you should always ultimately catch all exceptions in timer tasks.
    	            Log.e("TimerTick", "Timer Tick Failed.", t);            
    	        }
    	        
    	    //    System.out.println(" [x] Received '" + routingKey + "':'" + message + "'");   
    	      
    	    }
    	    catch  (Exception e) {
    	      e.printStackTrace();
    	    }
    	    
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (timer != null) {timer.cancel();}
        counter=0;
        nm.cancel(R.string.service_started); // Cancel the persistent notification.
       
        isRunning = false;
        try {
			channel.close();
			connection.close();
		} catch (IOException e) {
			 Log.i("MyService", "Service StoppedXXXXXXXXXXXXXXXXX.");
			e.printStackTrace();
		}
		 
        
    }
}