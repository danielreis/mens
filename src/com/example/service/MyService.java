/**@mainpage
 * 
 * <h1>Documenta&ccedil;&atilde;o - Servi&ccedil;o Android</h1>
 * 
 * <b>Autor</b>: Daniel de Oliveira Reis (<em>ei05106</em>)
 *
 * @version 0.3
 * @date 24 de Abril de 2012
 * 
 * <br><br><br>
 * @ref desc |
 * @ref serv |   
 * 
 * 
 * @page desc Descri&ccedil;&atilde;o geral
 * <h2>Descri&ccedil;&atilde;o geral</h2> <br>
 * 
 *
 * @note S&atilde;o necessários os ficheiros: X.lib
 * <br><br><br>
 * @ref desc |
 * @ref serv |
 *
 * @page serv  Funcionalidades
 * <h2></h2> <br>
 * 
 * @section sec Tipos de servi&ccedil;o fornecidos
 *	\li Comunica&ccedil;&atilde;o com o broker AMQP
 *  \li Comunica&ccedil;&atilde;o com o servidor REST 
 *  
 * <br><br><br>
 * @ref desc |
 * @ref serv |
 */



/*! \file MainActivity.java
 * \brief Desc fora da classe. Aparece na seao filelist
 */

/*! \class IncomingHandler
	 * \brief A test class.
	 *
	 * A more detailed class description2.
	 */


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
import android.widget.Toast;

import com.example.service.RestClient.RequestMethod;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ConsumerCancelledException;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.ShutdownSignalException;

/** @class MyService
 *  @brief Implementa&ccedil;&atilde;o do servi&ccedil;o que comunica com AMQP e possui métodos para realizar opera&ccedil;&otilde;es HTTP.
 * 
 *  Esta classe representa o servi&ccedil;o Android e possui capacidades de comunica&ccedil;&atilde;o com um Broker AMQP assim como
 *  a implementa&ccedil;&atilde;o de vários métodos úteis para a comunica&ccedil;ão com um servidor REST. 
 *  Implementa um sistema de notifica&ccedil;&otilde;es utilizado para assinalar a ocorrência de eventos.
 *  
 */
public class MyService extends Service 
{
	private NotificationManager nm; 
	private Timer timer =null; // Timer 
//	private int counter = 0, incrementby = 1;
	private static boolean isRunning = false; 
//	private String Rest_Host = "http://192.168.1.84:3000/";

	ArrayList<Messenger> mClients = new ArrayList<Messenger>();
	int mValue = 0;
	
	class current_subs
	{
		Timer t=null;
		String exch= null, rk = null;
		QueueingConsumer c;
		
		
	}
	
	ArrayList<current_subs> mySubs = new ArrayList<MyService.current_subs>(); 

	public static final int MSG_REGISTER_CLIENT = 1;
	public static final int MSG_UNREGISTER_CLIENT = 2;
//	public static final int MSG_SET_INT_VALUE = 3;
//	public static final int MSG_SET_STRING_VALUE = 4;
	public static final int MSG_CONNECT = 5;
//	public static final int MSG_CONNECT_QUEUE = 6;
//	public static final int MSG_POST_USER = 7;
//	public static final int MSG_PUT_USER = 8;
	public static final int MSG_GET_USER_REQUEST = 9;

	public static final int	MSG_GET_SENSOR_REQUEST = 10;
	public static final int MSG_MYSUBS = 15;
	public static final int MSG_CANCEL_SUBS = 20;

	
	
	// Target we publish for clients to send messages to IncomingHandler.
	final Messenger mMessenger = new Messenger(new IncomingHandler());

    /**
     * @brief descricao
     * @return string representativa do objecto Diferenca
     */
	@Override
	public IBinder onBind(Intent intent) 
	{
		Log.i("onBind()", intent.toString());
		showNotification("Service started");
		return mMessenger.getBinder();
	}

	/*! \class IncomingHandler
	 * \brief A test class.
	 *
	 * A more detailed class description.
	 */
	class IncomingHandler extends Handler 
	{ 
		
		@Override
		public void handleMessage(Message msg) 
		{
			switch (msg.what)
			{
			case MSG_REGISTER_CLIENT:
				mClients.add(msg.replyTo);
				break;
			case MSG_UNREGISTER_CLIENT:
				mClients.remove(msg.replyTo);
				break;
			case MSG_GET_USER_REQUEST:
				String url = msg.getData().get("url").toString();
				String rspMsg = GetRequest(url);
				
				
				//Send data as a String
				Bundle b = new Bundle();
				b.putString("rspData", rspMsg );
				notifyGetUsersRsp(b, MSG_GET_USER_REQUEST);
				break;
			case MSG_GET_SENSOR_REQUEST:
			{
				String url2 = msg.getData().get("url").toString();
				String rspMsg2 = GetRequest(url2);
				
				
				//Send data as a String
				Bundle b1 = new Bundle();
				b1.putString("rspData", rspMsg2 );
				notifyGetUsersRsp(b1, MSG_GET_SENSOR_REQUEST);
				break;
			}
			case MSG_CONNECT:
				try {
					String h = msg.getData().get("host").toString();
					String r = msg.getData().get("routing_key").toString();
					String e = msg.getData().get("exchange_name").toString();
					int p = msg.getData().getInt("port");

					Log.i("CONNECT:", h +":"+ r +":"+e+":"+ p);
					final QueueingConsumer consumer = connectRabbitMQ(h,p, e,r);
					current_subs sub = new current_subs();

					
					timer=new Timer();
					
					timer.scheduleAtFixedRate(new TimerTask(){ public void run() {subscribe(consumer);}}, 0, 1000);
					
					sub.t=timer;
					sub.rk=r;
					sub.exch=e;
					sub.c=consumer;
					mySubs.add(sub);
					
					isRunning = true;
					showNotification("Subscribe to" + timer.toString() + ":" + r);

				} catch (IOException e) {
					Log.i("CONNECT", "Connection error!");
					e.printStackTrace();
				}

				break;
				
			case MSG_MYSUBS:
				
				ArrayList<String> a = new ArrayList<String>();
				if(mySubs.size()!=0)
				{
					for (int i = 0 ; i< mySubs.size(); i++)
					{
						String line = i + ":" + mySubs.get(i).exch + ":" + mySubs.get(i).rk + ":"
								+ mySubs.get(i).t.toString();
						Log.i("SUBS", line );
						a.add(line);
						
						
					}
					
					
					
				//mySubs.get(0).t.cancel();
				
				}
				
				
				//Send data as a String
				Bundle b1 = new Bundle();
				b1.putStringArrayList("manage", a);
				notifyGetUsersRsp(b1, MSG_MYSUBS);
				
				
					
				break;
			case MSG_CANCEL_SUBS:
				int cancel_arr_pos = msg.getData().getInt("pos");
				
				try {
					mySubs.get(cancel_arr_pos).c.getChannel().close();
					
					if(mySubs.get(cancel_arr_pos).c.getChannel().isOpen())
					{
						Toast.makeText(getApplicationContext(), "STILL OPEN", Toast.LENGTH_SHORT).show();
						
					}
					else
					{
						Toast.makeText(getApplicationContext(), "CLOSED", Toast.LENGTH_SHORT).show();
						mySubs.remove(cancel_arr_pos);
					}
					
					
				} catch (IOException e) {
					Log.i("CANCEL SUB", "ERROR CANCELLING CONSUMER");
					e.printStackTrace();
				}
				
				
				
				
				
				
				
				
				break;
//			case MSG_CONNECT_QUEUE:
//				
//				try {
//					String h = msg.getData().get("host").toString();
//					String q = msg.getData().get("queue_name").toString();
//
//					final QueueingConsumer consumer = connectRabbitMQ2(h,q);
//
//					timer=new Timer();
//					timer.scheduleAtFixedRate(new TimerTask(){ public void run() {subscribe(consumer);}}, 0, 1000);
//					isRunning = true;
////					showNotification("Running");
//
//				} catch (IOException e) {
//					Log.i("CONNECT", "Connection error!");
//					e.printStackTrace();
//				}
//				
//				break;
//			case MSG_POST_USER:
//				PostUserToServer("John", "Doe", "100", "topic_logs");
//				break;
//			case MSG_PUT_USER:
//				PutUserToServer("D", "Reis", "28", "topic_logs", "2");
//				break;
			
				
//			case MSG_GET_SENSOR_REQUEST:
//				String url2 = msg.getData().get("url").toString();
//		//		Toast.makeText(getApplicationContext(), url2, Toast.LENGTH_SHORT).show();
//			
//				String rspMsg2 = GetRequest(url2);
//				Toast.makeText(getApplicationContext(), rspMsg2, Toast.LENGTH_SHORT).show();
//				//Send data as a String
//				Bundle b2 = new Bundle();
//				b2.putString("rspData", rspMsg2 );
//				Log.i("dsadads", "asdasdasdas");
//				notifyGetResponses(b2, MSG_GET_SENSOR_REQUEST);
//				break;

			default:
				super.handleMessage(msg);
			}
		}
	}
	
	private void notifyGetUsersRsp(Bundle b, int typeOfNotification) 
	{
		for (int i=mClients.size()-1; i>=0; i--) {
			try {
				
				Message msg = Message.obtain(null, typeOfNotification);
				msg.setData(b);
				mClients.get(i).send(msg);

			} catch (RemoteException e) {
				// The client is dead. Remove it from the list; we are going through the list from back to front so this is safe to do inside the loop.
				mClients.remove(i);
			}
		}
	}

	
//	private void sendMessageToUI(String message) 
//	{
//		for (int i=mClients.size()-1; i>=0; i--) {
//			try {
//				// Send data as an Integer
//			//	mClients.get(i).send(Message.obtain(null, MSG_SET_INT_VALUE, intvaluetosend, 0));
//				
//				//Send data as a String
//				Bundle b = new Bundle();
//				b.putString("rspData", message);
//				Message msg = Message.obtain(null, MSG_GET_USER_REQUEST);
//				msg.setData(b);
//				mClients.get(i).send(msg);
//
//			} catch (RemoteException e) {
//				// The client is dead. Remove it from the list; we are going through the list from back to front so this is safe to do inside the loop.
//				mClients.remove(i);
//			}
//		}
//	}

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
			//	Log.i("CONNECT", "Waiting for new messages from consumer" + consumer.getChannel());
				QueueingConsumer.Delivery delivery = consumer.nextDelivery();
			
				
				
				String message = new String(delivery.getBody());
				String routingKey = delivery.getEnvelope().getRoutingKey();
				String exch_name = delivery.getEnvelope().getExchange();
				
				
			
				showNotification("new message: " + message );
				Bundle b = new Bundle();
				b.putString("MSG", message);
				b.putString("EXCH", exch_name);
				b.putString("RK", routingKey);
				notifyGetUsersRsp( b, MSG_CONNECT); 
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
	public QueueingConsumer connectRabbitMQ(String host, int port, String exchange, String routing_key) throws IOException
	{
		Connection connection; // Conexão a utilizar
		Channel channel; // Canal de comunicação
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost(host);
		factory.setPort(port);

		Log.i("CONNECTION", "SET HOST OK");
		connection = factory.newConnection();
		channel = connection.createChannel();
		Log.i("CONNECTION", "CHANNEL OK");
		channel.exchangeDeclare(exchange, "topic");
		String queueName = channel.queueDeclare().getQueue();
		Log.i("CONNECTION", "QUEUE_NAME: " + queueName);
		
		channel.queueBind(queueName, exchange, routing_key);
		QueueingConsumer consumer = new QueueingConsumer(channel);
		channel.basicConsume(queueName, true, consumer);
	
		Log.i("CONNECTION", "CONSUMER: " + consumer.toString());
		
		return consumer;
	}
	
	
	
	/** 
	 * Send message to User Interface
	 * @param host Integer to send
	 * @param queue String to send
	 * @return something at all
	 */
//	public QueueingConsumer connectRabbitMQ2(String host, String queue) throws IOException
//	{
//		Connection connection; // Conexão a utilizar
//		Channel channel; // Canal de comunicação
//		ConnectionFactory factory = new ConnectionFactory();
//		factory.setHost(host);
//		
//
//		connection = factory.newConnection();
//		
//		channel = connection.createChannel();
//
//		channel.queueDeclare(queue, false, false, false, null);
//		
//	
//		QueueingConsumer consumer = new QueueingConsumer(channel);
//		channel.basicConsume(queue, true, consumer);
//
//		
//		return consumer;
//	}

	/**
	 * Called on stop service
	 */
	@Override
	public void onDestroy() {
		super.onDestroy();
		if (timer != null) {timer.cancel();}
//		counter=0;
		
		nm.cancel(R.string.service_started); // Cancel the persistent notification.

		isRunning = false;

	}
	
	/**
	 * REST PUT FUNCTION
	 * @param exchange_name 
	 */
//	public void PutUserToServer(String first_name, String last_name, String age, String exchange_name,  String user_id)
//	{
//		String h = Rest_Host + "users/" + user_id;
//		RestClient client = new RestClient(h);
//		
//		
//		client.AddParam("user[first_name]", first_name);
//		client.AddParam("user[last_name]", last_name);
//		client.AddParam("user[age]", age);
//		client.AddParam("user[exchange_name]", exchange_name);
//		
//		
//	
//
//		try 
//		{
//			client.Execute(RequestMethod.PUT);
//			if(client.getResponseCode() == 200)
//				Toast.makeText(getApplicationContext(), "OK", Toast.LENGTH_LONG).show();
//			else
//				Toast.makeText(getApplicationContext(), "ERRO", Toast.LENGTH_LONG).show();
//			
//		} 
//		catch (Exception e) 
//		{
//			Toast.makeText(getApplicationContext(), "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show();
//			e.printStackTrace();
//		}
//
//		
//		
//	}
//	
	
	
	/**
	 * REST POST FUNCTION
	 */
//	public void PostUserToServer(String first_name, String last_name, String age, String exchange)
//	{
//		String h = Rest_Host + "users";
//		RestClient client = new RestClient(h);
//		
//		client.AddParam("user[first_name]", first_name);
//		client.AddParam("user[last_name]", last_name);
//		client.AddParam("user[age]", age);
//		client.AddParam("user[exchange_name]", exchange);
//		
//	
//
//		try 
//		{
//			client.Execute(RequestMethod.POST);
//			
//		} 
//		catch (Exception e) 
//		{
//			Toast.makeText(getApplicationContext(), "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show();
//			e.printStackTrace();
//		}
//
//		
//		if(client.getResponseCode() == 302)
//			Toast.makeText(getApplicationContext(), "Dados submetidos com sucesso! ", Toast.LENGTH_LONG).show();
//		else
//			Toast.makeText(getApplicationContext(), "ERRO", Toast.LENGTH_LONG).show();
//	}
	
	/**
	 * REST GET FUNCTION
	 */
	public String GetRequest(String url)
	{
		String rsp  = "";
		RestClient client = new RestClient(url);
		
		client.AddHeader("Accept", "application/json");
		
		try 
		{
			client.Execute(RequestMethod.GET);
			rsp = client.getResponse();
		
			
		} 
		catch (Exception e) 
		{
			Toast.makeText(getApplicationContext(), "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show();
			e.printStackTrace();
		}

		
		
//		if(client.getResponseCode() == 200)
//		//	Toast.makeText(getApplicationContext(), "Dados submetidos com sucesso! ", Toast.LENGTH_LONG).show();
//		else
//			Toast.makeText(getApplicationContext(), "ERRO", Toast.LENGTH_LONG).show();
		
		return rsp;
	}
	
	
	
	
}