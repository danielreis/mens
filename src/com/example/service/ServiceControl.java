package com.example.service;

import java.util.ArrayList;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.ToggleButton;

/** @class ServiceControl
*  @brief Atividade que controla a execu&ccedil;&atilde;o do servi&ccedil;o. 
*  
*  Apesar de aplica&ccedil;&otilde;es cliente poderem iniciar o servi&ccedil;o sem problemas, faz sentido existir uma aplica&ccedil;&atilde;o que o possa iniciar / parar.
*/
public class ServiceControl extends Activity 
{
	ToggleButton serviceTogglebtn;
	TextView textMessages;
	Messenger mService = null;
	boolean mIsBound;
	final Messenger mMessenger = new Messenger(new IncomingHandler());
	ArrayList<String> listItems=new ArrayList<String>();
	ArrayAdapter<String> adapter;


	/** 
	 * @class IncomingHandler
	 * @brief Classe respons&aacute;vel por receber mensagens do servi&ccedil;o.
	 */
	class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {

			default:
				super.handleMessage(msg);
			}
		}
	}
	
	/**
	 * Regista atividade no servi&ccedil;o
	 */
	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mService = new Messenger(service);
			try {
				Message msg = Message.obtain(null, MyService.MSG_REGISTER_CLIENT);
				msg.replyTo = mMessenger;
				mService.send(msg);
			} catch (RemoteException e) {

			}
		}

		public void onServiceDisconnected(ComponentName className) 
		{
			mService = null;
		}
	};

	/**
	 * @brief Executa sempre que o servi&ccedil;o &eacute; iniciado.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		CheckIfServiceIsRunning();
	}
	
	/**
	 * @brief Verifica se o servi&ccedil;o encontra-se em execu&ccedil;&atilde;o. Caso esteja, faz bind ao servi&ccedil;o.
	 */
	private void CheckIfServiceIsRunning() 
	{
		if (MyService.isRunning()) {
			doBindService();
		}
	}

	/**
	 * Handler do bot&atilde;o apresentado na interface 
	 * @param v Vista atual
	 */
	public void onToggleClicked(View v) {
		// Perform action on clicks
		if (((ToggleButton) v).isChecked()) {
			Intent i = new Intent(ServiceControl.this, MyService.class);
			startService(i);
			doBindService();
		} else {
			doUnbindService();
			stopService(new Intent(ServiceControl.this, MyService.class));
		}
	}

	
	/**
	 * Coneta-se ao servi&ccedil;o
	 */
	void doBindService() 
	{
		mIsBound = bindService(new Intent(this, MyService.class), mConnection, Context.BIND_AUTO_CREATE);	
	}
	
	/**
	 * Desconeta-se do servi&ccedil;o.
	 */
	void doUnbindService() 
	{
		if (mIsBound) 
		{
			if (mService != null) {
				try {
					Message msg = Message.obtain(null, MyService.MSG_UNREGISTER_CLIENT);
					msg.replyTo = mMessenger;
					mService.send(msg);
				} catch (RemoteException e) 
				{
					// There is nothing special we need to do if the service has crashed.
				}
			}
			unbindService(mConnection);
			mIsBound = false;

		}
	}

	/**
	 * Executa ao sair da aplica&ccedil;&atilde;o.
	 */
	@Override
	protected void onDestroy() 
	{
		super.onDestroy();
		try {
			doUnbindService();
		} catch (Throwable t) {
			Log.i("Error", "Unbind failed", t);
		}
	}
}