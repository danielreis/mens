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


/**
 * Implementação de um serviço local em android. 
 * @author danielreis
 */
public class MyService extends Service 
{
	private static final String EXCHANGE_NAME = "topic_logs"; // Exchange utilizado para publicar e subscrever mensagens
	private String HOST = "192.168.1.84"; // IP do broker de mensagens na rede
	private String ROUTING_KEY = "anonymous.info";
	
	private QueueingConsumer consumer  = null; // consumer para recepcionar mensagens
	private Connection connection; // Conexão a utilizar
	private Channel channel; // Canal de comunicação

	private NotificationManager nm; // Sistema de notificações
	private Timer timer = new Timer(); // Timer 
	private int counter = 0, incrementby = 1; // contadores
	private static boolean isRunning = false; // TRUE se o serviço estiver em execução

	ArrayList<Messenger> mClients = new ArrayList<Messenger>(); // Mantem registo de todos os clientes que subscrevem notificações
	int mValue = 0; // Último valor enviado pelo cliente.

	static final int MSG_REGISTER_CLIENT = 1;
	static final int MSG_UNREGISTER_CLIENT = 2;
	static final int MSG_SET_INT_VALUE = 3;
	static final int MSG_SET_STRING_VALUE = 4;
	final Messenger mMessenger = new Messenger(new IncomingHandler()); // Target we publish for clients to send messages to IncomingHandler.


	@Override
	public IBinder onBind(Intent intent) 
	{
		Log.i("onBind()", intent.toString());
		return mMessenger.getBinder();
	}

	/**
	 *  Handler of incoming messages from clients.
	 */
	class IncomingHandler extends Handler { 
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

		Log.i("onCreate()", "STARTED!");
		try {
			consumer = connectRabbitMQ(HOST,EXCHANGE_NAME, ROUTING_KEY);
			showNotification();
			timer.scheduleAtFixedRate(new TimerTask(){ public void run() {subscribe();}}, 0, 1000);
			isRunning = true;
		} catch (IOException e) {
			Log.i("CONNECT", "Connection error!");
			e.printStackTrace();
		}

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

	/**
	 * onStartCommand only runs when client explicit calls startService().
	 * If client only bind, this method never executes
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i("MyService", "Received start id " + startId + ": " + intent);
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
	private void subscribe() 
	{
		if(consumer!=null)
		{
			try 
			{
				QueueingConsumer.Delivery delivery = consumer.nextDelivery();
				String message = new String(delivery.getBody());
				String routingKey = delivery.getEnvelope().getRoutingKey();

				counter += incrementby;
				sendMessageToUI(counter, message + ":" + routingKey);
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
			Log.i("CONNECT", "Consumer is null at subscribe()");
		}

	}

	/**
	 * Connect to RabbitMQ broker
	 * @throws IOException  if something crashed
	 */
	public QueueingConsumer connectRabbitMQ(String host, String exchange, String routing_key) throws IOException
	{
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost(host);

		connection = factory.newConnection();
		channel = connection.createChannel();

		channel.exchangeDeclare(exchange, "topic");
		String queueName = channel.queueDeclare().getQueue();

		channel.queueBind(queueName, exchange, routing_key);

		consumer = new QueueingConsumer(channel);
		channel.basicConsume(queueName, true, consumer);

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