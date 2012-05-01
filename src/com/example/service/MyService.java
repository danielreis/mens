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
 * @note S&atilde;o necess&aacute;rios os ficheiros: X.lib
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

import com.example.service.ReSTUtils.RequestMethod;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ConsumerCancelledException;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.ShutdownSignalException;

/** @class MyService
 *  @brief Implementa&ccedil;&atilde;o do servi&ccedil;o que comunica com AMQP e possui m&eacute;todos para realizar opera&ccedil;&otilde;es HTTP.
 * 
 *  Esta classe representa o servi&ccedil;o Android e possui capacidades de comunica&ccedil;&atilde;o com um Broker AMQP assim como
 *  a implementa&ccedil;&atilde;o de vários métodos úteis para a comunica&ccedil;ão com um servidor REST. 
 *  Implementa um sistema de notifica&ccedil;&otilde;es utilizado para assinalar a ocorrência de eventos.
 *  
 */
public class MyService extends Service 
{
	private NotificationManager nm; 	/**< Gestor de notifica&ccedil;&otilde;es do android */
	private Timer timer =null; 			/**< Timer utilizado para consumir mensagens */
	private static boolean isRunning = false; 

	ArrayList<Messenger> mClients = new ArrayList<Messenger>(); /*!< Lista de clientes ligados ao servi&ccedil;o */


	/** @class SubscriptionObject
	 *  @brief Objeto que cont&eacute;m detalhes das subscri&ccedil;&otilde;es AMQP efetuadas
	 *
	 * Este objeto &eacute; composto por 1 Timer, o nome do exchange e da routing key assim como o consumidor. 
	 * É este consumidor que perite cancelar as subscriç&ccedil;&otilde;es.
	 */
	class SubscriptionObject
	{
		Timer t=null;
		String exch= null, rk = null;
		QueueingConsumer c;	
	}

	
	ArrayList<SubscriptionObject> mySubs = new ArrayList<MyService.SubscriptionObject>();  /**< Array que guarda as subscri&ccedil;&otilde;es efetuadas */ 

	public static final int MSG_REGISTER_CLIENT = 1; 	/**< Mensagem enviada pelo cliente que o subscreve no servi&ccedil;o */
	public static final int MSG_UNREGISTER_CLIENT = 2; 	/**< Mensagem enviada pelo cliente que deseja cancela a sua subscri&ccedil;&atilde;o no servi&ccedil;o */ 
	public static final int MSG_POST_USER = 7; 			/**< Mensagem enviada pelo cliente para registo no servidor ReST */
	public static final int MSG_GET_USER_REQUEST = 9;  	/**< Mensagem enviada pelo cliente para obter a lista de utilizadores */
	public static final int	MSG_GET_SENSOR_REQUEST = 10; /**< Mensagem enviada pelo cliente para obter a lista de sensores de um determinado utilizador */
	public static final int MSG_CONNECT_AMQP = 5;  		/**< Mensagem enviada pelo cliente para subscrever determinado evento */
	public static final int MSG_MYSUBS = 15; 			/**< Mensagem enviada pelo cliente para obter a sua lista de subscri&ccedil;&otilde;es */
	public static final int MSG_CANCEL_SUBS = 20; 		/**< Mensagem enviada pelo cliente que cancela subscri&ccedil;&otilde;es de eventos */
//	public static final int MSG_PUT_USER = 8; 			/**< Mensagem enviada pelo cliente que ... */


	final Messenger mMessenger = new Messenger(new IncomingHandler()); /**< A fun&ccedil;&atilde;o passada em argumento recebe os pedidos dos clientes que utilizam a classe Messenger para interagir com o servi&ccedil;o */

	/**
	 * @brief Fornece forma de atividades externas conectarem-se ao servi&ccedil;o
	 * 
	 * Apresenta uma notifica&ccedil;&atilde;o no Android cada vez que uma aplica&ccedil;&atilde;o cliente se liga ao servi&ccedil;o
	 * @return IBinder Objeto que clientes utilizam para conectarem-se ao servi&ccedil;o
	 */
	@Override
	public IBinder onBind(Intent intent) 
	{
		showNotification("Service started");
		return mMessenger.getBinder();
	}

	/** @class IncomingHandler
	 * @brief Objeto respons&aacute;vel pela rece&ccedil;&atilde;o e execu&ccedil;&atilde;o dos pedidos de clientes 
	 *
	 */
	class IncomingHandler extends Handler 
	{ 
		/** 
		 * @brief  Respons&aacute;vel pela rece&ccedil;&atilde;o e execu&ccedil;&atilde;o dos pedidos de clientes 
		 *
		 *	Tipos de pedidos:
		 *	\li MSG_REGISTER_CLIENT - Adiciona o cliente ao array de clientes para que possam ser posteriormente notificados pelo servi&ccedil;o; @sa MSG_REGISTER_CLIENT
		 *  \li MSG_UNREGISTER_CLIENT - Remove o cliente do array de clientes para que n&atilde;o seja mais notificado pelo servi&ccedil;o; @sa MSG_UNREGISTER_CLIENT
		 *  \li MSG_GET_USER_REQUEST - O servi&ccedil;o realiza um pedido HTTP GET ao URL passado juntamente com a mensagem. Neste caso para receber informa&ccedil;&otilde;es sobre utilizadores registados no sistema; @sa MSG_GET_USER_REQUEST
		 *  \li MSG_GET_SENSOR_REQUEST - O servi&ccedil;o realiza um pedido HTTP GET ao URL passado juntamente com a mensagem. Neste caso para receber informa&ccedil;&otilde;es sobre utilizadores registados no sistema; @sa MSG_GET_SENSOR_REQUEST
		 *  \li MSG_CONNECT_AMQP - Cliente deseja subscrever notifica&ccedil;&otilde;es acerca de determinado utilizador / sensor enviando a informa&ccedil;&atilde;o (nome do "exchange", chave de roteamento, localiza&ccedil;&atilde;o do broker e porta) para subscrever determinado t&oacute;pico ; @sa MSG_CONNECT_AMQP
		 *  \li MSG_MYSUBS - Devolve ao cliente um array com as informa&ccedil;&otilde;es relacionadas com as suas subscri&ccedil;&otilde;es; @sa MSG_MYSUBS
		 *  \li MSG_CANCEL_SUBS - Cancela determinada subscri&ccedil;&atilde;o  ; @sa MSG_CANCEL_SUBS
		 *  \li MSG_POST_USER - O servi&ccedil;o realiza um pedido HTTP POST ao URL passado juntamente com a mensagem. Neste caso para registar um novo utilizador no sistema. @sa MSG_POST_USER
		 */
		@Override
		public void handleMessage(Message msg) 
		{
			String rspMsg = null;
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

				rspMsg = GetRequest(url);

				if(rspMsg != null )
				{
					Bundle b = new Bundle();
					b.putString("rspData", rspMsg );
					notifyClients(b, MSG_GET_USER_REQUEST);
				}

				break;
			case MSG_GET_SENSOR_REQUEST:
			{
				url = msg.getData().get("url").toString();
				rspMsg = GetRequest(url);
				if(rspMsg != null )
				{
					Bundle b = new Bundle();
					b.putString("rspData", rspMsg );
					notifyClients(b, MSG_GET_SENSOR_REQUEST);
				}
				break;
			}
			case MSG_CONNECT_AMQP:
				try {
					String h = msg.getData().get("host").toString();
					String r = msg.getData().get("routing_key").toString();
					String e = msg.getData().get("exchange_name").toString();
					int p = msg.getData().getInt("port");

					Log.i("CONNECT:", h +":"+ r +":"+e+":"+ p);
					final QueueingConsumer consumer = connectRabbitMQ(h,p, e,r);

					timer=new Timer();
					timer.scheduleAtFixedRate(new TimerTask(){ public void run() {subscribeTopic(consumer);}}, 0, 1000);

					SubscriptionObject sub = new SubscriptionObject();
					sub.t=timer;
					sub.rk=r;
					sub.exch=e;
					sub.c=consumer;
					mySubs.add(sub);

					isRunning = true;
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
				}

				Bundle b1 = new Bundle();
				b1.putStringArrayList("manage", a);
				notifyClients(b1, MSG_MYSUBS);
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
						Toast.makeText(getApplicationContext(), "Channel closed", Toast.LENGTH_SHORT).show();
						mySubs.remove(cancel_arr_pos);
					}


				} catch (IOException e) {
					Log.i("CANCEL SUB", "ERROR CANCELLING CONSUMER");
					e.printStackTrace();
				}


				break;
			case MSG_POST_USER:
				String host = msg.getData().get("host").toString();
				String user_fname = msg.getData().get("fname").toString();
				String user_lname = msg.getData().get("lname").toString();
				int user_age = msg.getData().getInt("age");

				Log.i("ARGS", host + ":" + user_fname + ":" +  user_lname + ":" + user_age);
				PostUserToServer(host, user_fname, user_lname, String.valueOf(user_age), user_fname + "_" + user_lname + "_" + String.valueOf(user_age));
				break;

			default:
				super.handleMessage(msg);
			}
		}
	}

	/**
	 * @brief Notifica clientes com a possibilidade de passar informa&ccedil;&otilde;es extra
	 * @param b Objeto que cont&eacute;m informa&ccedil&atilde;o &uacute;til para o cliente;
	 * @param typeOfNotification Tipo de notifica&ccedil&atilde;o 
	 */
	private void notifyClients(Bundle b, int typeOfNotification) 
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

	




	/**
	 * @brief Executa sempre que o servi&ccedil;o &eacute; iniciado.
	 */
	@Override
	public void onCreate() {
		super.onCreate();
	}
	
	/**
	 * M&eacute;todo que permite apresentar notifica&ccedil;&otilde;es no sistema Android
	 * @param text Texto que aparece na notifica&ccedil;&atildeo
	 */
	private void showNotification(String text) 
	{

		SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss");
		String formattedDate = df.format(Calendar.getInstance().getTime());

		nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		// In this sample, we'll use the same text for the ticker and the expanded notification

		// Set the icon, scrolling text and timestamp
		Notification notification = new Notification(R.drawable.custom_icon, text + " @" + formattedDate, System.currentTimeMillis());
		// The PendingIntent to launch our activity if the user selects this notification
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, ServiceControl.class), 0);
		// Set the info for the views that show in the notification panel.
		notification.setLatestEventInfo(this, getText(R.string.service_label), text, contentIntent);
		// Send the notification.
		// We use a layout id because it is a unique number.  We use it later to cancel.
		nm.notify(R.string.service_started, notification);
	}

	
	/**
	 * onStartCommand apenas executa quando o cliente explicitamente chama o m&eacute;todo startService().
	 * 
	 * N&atilde;o executa se cliente apenas fizer bind ao servi&ccedil;o
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i("MyService", "Received start id " + startId + ": " + intent);
		showNotification("Service started");
		return START_STICKY; /**< Executa at&eacute; explicitamente parado; */
	}

	/**
	 * Verifica se o servi&ccedil;o se encontra em execu&ccedil;&atilde;o
	 * @return Verdadeiro se o servi&ccedil;o estiver a correr
	 */
	public static boolean isRunning()
	{
		return isRunning;
	}

	/**
	 * M&eacute;todo respons&aacute;vel pela subscri&ccedil;&atilde;o de um determinado recurso e posterior notifica&ccedil;&atilde;o ao cliente.
	 * @param consumer &Eacute; necess&aacute;rio um consumidor para efetuar uma subscr&ccedil;&atilde;o 
	 */
	private void subscribeTopic(QueueingConsumer consumer) 
	{
		if(consumer!=null)
		{
			try 
			{
				QueueingConsumer.Delivery delivery = consumer.nextDelivery();

				String message = new String(delivery.getBody());
				String routingKey = delivery.getEnvelope().getRoutingKey();
				String exch_name = delivery.getEnvelope().getExchange();
				showNotification("new message: " + message );
				Bundle b = new Bundle();
				b.putString("MSG", message);
				b.putString("EXCH", exch_name);
				b.putString("RK", routingKey);
				notifyClients( b, MSG_CONNECT_AMQP); 
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
	 * Coneta-se ao broker AMQP utilizando uma difus&atilde;o de mensagens do tipo "Topic"
	 * @param host Ip do servidor de mensagens (broker AMQP)
	 * @param port Porta do servidor de mensagens (broker AMQP)
	 * @param exchange Nome do exchange para realizar a subscri&ccedil;&atilde;o do t&oacute;pico
	 * @param routing_key Chave de roteamento para realizar a subscri&ccedil;&atilde;o do t&oacute;pico
	 * @return Objeto consumidor gerado a partir das informa&ccedil;&otilde;es passadas em argumento
	 * @throws IOException se acontecer algum erro
	 */
	public QueueingConsumer connectRabbitMQ(String host, int port, String exchange, String routing_key) throws IOException
	{
		Connection connection; 
		Channel channel; 
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost(host);
		factory.setPort(port);
		connection = factory.newConnection();
		channel = connection.createChannel();
		channel.exchangeDeclare(exchange, "topic");
		String queueName = channel.queueDeclare().getQueue();
		channel.queueBind(queueName, exchange, routing_key);
		QueueingConsumer consumer = new QueueingConsumer(channel);
		channel.basicConsume(queueName, true, consumer);
		return consumer;
	}


	/**
	 * @brief Opera&ccedil;&otilde;es a executar caso o servi&ccedil;o seja parado 
	 */
	@Override
	public void onDestroy() 
	{
		super.onDestroy();
		
		for(SubscriptionObject obj : mySubs)
		{
			try {
				obj.c.getChannel().close();
			} catch (IOException e) {
				Log.i("ERROR", "onDestroy: " + e.getLocalizedMessage());
				e.printStackTrace();
			}
		}
		
		mySubs.clear();
		
		if (timer != null) {timer.cancel();}	
		nm.cancel(R.string.service_started); 
		isRunning = false;
	}

	/**
	 * Regista um utilizador no sistema. 
	 * @param host URL do recurso
	 * @param first_name Primeiro nome do utilizador
	 * @param last_name &Uacute;ltimo nome do utilizador
	 * @param age Idade do utilizador
	 * @param exchange Nome do exchange (first_name_last_name_age)
	 */
	public void PostUserToServer(String host, String first_name, String last_name, String age, String exchange)
	{
		int reqOK = 201;

		ReSTUtils client = new ReSTUtils(host);

		client.AddParam("user[first_name]", first_name);
		client.AddParam("user[last_name]", last_name);
		client.AddParam("user[age]", age);
		client.AddParam("user[exchange_name]", exchange);
		client.AddHeader("Accept", "application/json");
		try 
		{
			client.Execute(RequestMethod.POST);

		} 
		catch (Exception e) 
		{
			Toast.makeText(getApplicationContext(), "Erro: utilizador já existente" + e.getMessage(), Toast.LENGTH_LONG).show();
			e.printStackTrace();
		}


		if(client.getResponseCode() == reqOK)
		{
		
			Bundle b = new Bundle();
			b.putInt("rsp_code", reqOK);
			b.putString("rsp_data",client.getResponse());

			notifyClients(b, MSG_POST_USER);


		}
		else
		{
			String k = String.valueOf(client.getResponseCode());
			Toast.makeText(getApplicationContext(),k , Toast.LENGTH_LONG).show();

		}
	}

	/**
	 * Realiza pedidos HTTP GET sobre o recurso passado em argumento
	 * @param url URL que se deseja consultar
	 * @return String com a resposta do servidor ReST
	 */
	public String GetRequest(String url)
	{
		String rsp  = "";
		ReSTUtils client = new ReSTUtils(url);

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
