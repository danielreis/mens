package com.example.service;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
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

public class MyService extends Service 
{
	private NotificationManager nm; 
	private Timer timer =null; // Timer 
	private int counter = 0, incrementby = 1;
	private static boolean isRunning = false; 

	ArrayList<Messenger> mClients = new ArrayList<Messenger>();
	int mValue = 0;

	public static final int MSG_REGISTER_CLIENT = 1;
	public static final int MSG_UNREGISTER_CLIENT = 2;
	public static final int MSG_SET_INT_VALUE = 3;
	public static final int MSG_SET_STRING_VALUE = 4;
	public static final int MSG_CONNECT = 5;
	public static final int MSG_CONNECT_QUEUE = 6;
	
	// Target we publish for clients to send messages to IncomingHandler.
	final Messenger mMessenger = new Messenger(new IncomingHandler());

	@Override
	public IBinder onBind(Intent intent) 
	{
		Log.i("onBind()", intent.toString());
		return mMessenger.getBinder();
	}

	/**
	 *  Handler of incoming messages from clients.
	 */
	class IncomingHandler extends Handler 
	{ 
		@Override
		public void handleMessage(Message msg) 
		{
			switch (msg.what) {
			case MSG_REGISTER_CLIENT:
				mClients.add(msg.replyTo);
				break;
			case MSG_UNREGISTER_CLIENT:
				mClients.remove(msg.replyTo);
				break;
			case MSG_CONNECT:
				try {
					String h = msg.getData().get("host").toString();
					String r = msg.getData().get("routing_key").toString();
					String q = msg.getData().get("queue_name").toString();

					final QueueingConsumer consumer = connectRabbitMQ(h,q,r);

					timer=new Timer();
					timer.scheduleAtFixedRate(new TimerTask(){ public void run() {subscribe(consumer);}}, 0, 1000);
					isRunning = true;
					showNotification("Running");

				} catch (IOException e) {
					Log.i("CONNECT", "Connection error!");
					e.printStackTrace();
				}

				break;
			case MSG_CONNECT_QUEUE:
				
				try {
					String h = msg.getData().get("host").toString();
					String q = msg.getData().get("queue_name").toString();

					final QueueingConsumer consumer = connectRabbitMQ2(h,q);

					timer=new Timer();
					timer.scheduleAtFixedRate(new TimerTask(){ public void run() {subscribe(consumer);}}, 0, 1000);
					isRunning = true;
					showNotification("Running");

				} catch (IOException e) {
					Log.i("CONNECT", "Connection error!");
					e.printStackTrace();
				}
				
				break;

			default:
				super.handleMessage(msg);
			}
		}
	}

	/**
	 * Send message to User Interface
	 * @param intvaluetosend Integer to send
	 * @param message String to send
	 */
	private void sendMessageToUI(int intvaluetosend, String message) 
	{
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
	}

	private void showNotification(String text) {
		
		SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss");
        String formattedDate = df.format(Calendar.getInstance().getTime());
		
		nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		// In this sample, we'll use the same text for the ticker and the expanded notification
		
		// Set the icon, scrolling text and timestamp
		Notification notification = new Notification(R.drawable.ic_launcher, text + " @" + formattedDate, System.currentTimeMillis());
		// The PendingIntent to launch our activity if the user selects this notification
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
		// Set the info for the views that show in the notification panel.
		notification.setLatestEventInfo(this, getText(R.string.service_label), text, contentIntent);
		// Send the notification.
		// We use a layout id because it is a unique number.  We use it later to cancel.
		nm.notify(R.string.service_started, notification);
	}

	/**
	 * onStartCommand only runs when client explicit calls startService().
	 * If client only bind, this method never executes
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i("MyService", "Received start id " + startId + ": " + intent);

		showNotification("Service started");
		return START_STICKY; // run until explicitly stopped.
	}

	/**
	 * Returns service status
	 * @return true if server is running
	 */
	public static boolean isRunning()
	{
		return isRunning;
	}

	/**
	 * Task that service performs
	 */
	private void subscribe(QueueingConsumer consumer) 
	{
		if(consumer!=null)
		{
			try 
			{
//				Log.i("CONNECT", "Waiting for new messages from consumer" + consumer.getChannel());
				QueueingConsumer.Delivery delivery = consumer.nextDelivery();
//				Log.i("CONNECT", "MESSAGE ARRIVED");
				String message = new String(delivery.getBody());
//				String routingKey = delivery.getEnvelope().getRoutingKey();
				String exch_name = delivery.getEnvelope().getExchange();
				String queue_name = delivery.getEnvelope().getRoutingKey();
				
//				Log.i("MESSAGE ARRIVED", message);
				showNotification(message);
				counter += incrementby;
				sendMessageToUI(counter, message + ":" + exch_name + ":" + queue_name);
			}

			catch (ShutdownSignalException e) 
			{
				e.printStackTrace();

			} catch (ConsumerCancelledException e) {

				e.printStackTrace();
			} catch (InterruptedException e) {

				e.printStackTrace();
			}
		}
		else
		{
			Log.i("CONNECT", "ERROR: Consumer is null at subscribe()");
		}

	}
	
	


	/**
	 * Connect to RabbitMQ broker
	 * @throws IOException  if something crashed
	 */
	public QueueingConsumer connectRabbitMQ(String host, String exchange, String routing_key) throws IOException
	{
		Connection connection; // Conexão a utilizar
		Channel channel; // Canal de comunicação
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost(host);

//		Log.i("CONNECTION", "SET HOST OK");
		connection = factory.newConnection();
		channel = connection.createChannel();
//		Log.i("CONNECTION", "CHANNEL OK");
		channel.exchangeDeclare(exchange, "topic");
		String queueName = channel.queueDeclare().getQueue();
//		Log.i("CONNECTION", "QUEUE_NAME: " + queueName);
		
		channel.queueBind(queueName, exchange, routing_key);
		QueueingConsumer consumer = new QueueingConsumer(channel);
		channel.basicConsume(queueName, true, consumer);
		
//		Log.i("CONNECTION", "CONSUMER: " + consumer.toString());
		
		return consumer;
	}
	
	
	public QueueingConsumer connectRabbitMQ2(String host, String queue) throws IOException
	{
		Connection connection; // Conexão a utilizar
		Channel channel; // Canal de comunicação
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost(host);

		connection = factory.newConnection();
		
		channel = connection.createChannel();

		channel.queueDeclare(queue, false, false, false, null);
		
	
		QueueingConsumer consumer = new QueueingConsumer(channel);
		channel.basicConsume(queue, true, consumer);

		
		return consumer;
	}

	/**
	 * Called on stop service
	 */
	@Override
	public void onDestroy() {
		super.onDestroy();
		if (timer != null) {timer.cancel();}
		counter=0;
		nm.cancel(R.string.service_started); // Cancel the persistent notification.

		isRunning = false;

	}
}