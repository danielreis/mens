package com.example.service;

import com.example.service.R;

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
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    Button btnStart, btnStop, btnUpby1, btnUpby10;
    TextView textStatus, textIntValue, textMessages;
    Messenger mService = null;
    boolean mIsBound;
    final Messenger mMessenger = new Messenger(new IncomingHandler());

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MyService.MSG_SET_INT_VALUE:
                textIntValue.setText("Counter: " + msg.arg1);
                break;
            case MyService.MSG_SET_STRING_VALUE:
                String str1 = msg.getData().getString("str1");
                textMessages.append("\n" + str1);
                break;
            default:
                super.handleMessage(msg);
            }
        }
    }
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = new Messenger(service);
            textStatus.setText("Attached.");
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
            textStatus.setText("Disconnected.");
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        btnStart = (Button)findViewById(R.id.btnStart);
        btnStop = (Button)findViewById(R.id.btnStop);
   
        textStatus = (TextView)findViewById(R.id.textStatus);
        textIntValue = (TextView)findViewById(R.id.textIntValue);
       
        btnUpby1 = (Button)findViewById(R.id.btnUpby1);
        btnUpby10 = (Button)findViewById(R.id.btnUpby10);

        btnStart.setOnClickListener(btnStartListener);
        btnStop.setOnClickListener(btnStopListener);
      
      
        btnUpby1.setOnClickListener(btnUpby1Listener);
        btnUpby10.setOnClickListener(btnUpby10Listener);

        textMessages = (TextView)findViewById(R.id.textViewMessages);
    
     
        
        restoreMe(savedInstanceState);

        CheckIfServiceIsRunning();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("textStatus", textStatus.getText().toString());
        outState.putString("textIntValue", textIntValue.getText().toString());
       
    }
    private void restoreMe(Bundle state) {
        if (state!=null) {
            textStatus.setText(state.getString("textStatus"));
            textIntValue.setText(state.getString("textIntValue"));
         
        }
    }
    private void CheckIfServiceIsRunning() 
    {
        if (MyService.isRunning()) {
            doBindService();
        }
    }

    private OnClickListener btnStartListener = new OnClickListener() {
        public void onClick(View v){
//        	 Bundle b = new Bundle();
//             b.putString("host", "192.168.1.84");
//             b.putString("routing_key", "anonymous.info");
//             b.putString("exchange_name", "topic_logs");
             Intent i = new Intent(MainActivity.this, MyService.class);
//            i.putExtras(b);

             startService(i);
        
            doBindService();
        }
    };
    private OnClickListener btnStopListener = new OnClickListener() 
    {
        public void onClick(View v){
            doUnbindService();
            stopService(new Intent(MainActivity.this, MyService.class));
        }
    };
  
    private OnClickListener btnUpby1Listener = new OnClickListener() 
    {
        public void onClick(View v){
        	sendConnectInfo("192.168.1.84", "anonymous.info", "topic_logs");
        	Toast.makeText(v.getContext(), "Listen to topic_logs", Toast.LENGTH_LONG).show();
        }
    };
    private OnClickListener btnUpby10Listener = new OnClickListener() 
    {
    	public void onClick(View v){
        	sendConnectInfo("192.168.1.84", "anonymous.info", "another_queue");
        	Toast.makeText(v.getContext(), "Listen to another_queue", Toast.LENGTH_LONG).show();
        }
    };
    
    
//    private void sendMessageToService(int intvaluetosend, String message) 
//    {
//        if (mIsBound) {
//            if (mService != null) {
//                try {
//                    Message msg = Message.obtain(null, MyService.MSG_SET_INT_VALUE, intvaluetosend, 0);
//                    msg.replyTo = mMessenger;
//                    mService.send(msg);
//                    
//                    Bundle b = new Bundle();
//    				b.putString("str1", message);
//    				Message msg1 = Message.obtain(null, MyService.MSG_SET_STRING_VALUE);
//    				msg1.setData(b);
//    				 msg1.replyTo = mMessenger;
//                     mService.send(msg1);
//                } catch (RemoteException e) {
//                }
//            }
//        }
//    }
    
    private void sendConnectInfo(String host, String routing_key, String queue_name) 
    {
        if (mIsBound) {
            if (mService != null) {
                try {
                    
                    Bundle b = new Bundle();
    				b.putString("host", host);
    				b.putString("routing_key", routing_key);
    				b.putString("queue_name", queue_name);
    				Message msg = Message.obtain(null, MyService.MSG_CONNECT);
    				msg.setData(b);
    				msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e) {
                	Log.i("ERROR", "SendConnectInfo()");
                }
            }
        }
    }


    void doBindService() 
    {
    	mIsBound = bindService(new Intent(this, MyService.class), mConnection, Context.BIND_AUTO_CREATE);
       
        textStatus.setText("Binding:" + mIsBound);
    }
    void doUnbindService() 
    {
        if (mIsBound) {
            // If we have received the service, and hence registered with it, then now is the time to unregister.
            if (mService != null) {
                try {
                    Message msg = Message.obtain(null, MyService.MSG_UNREGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service has crashed.
                }
            }
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
            textStatus.setText("Unbinding.");
        }
    }

    @Override
    protected void onDestroy() 
    {
        super.onDestroy();
        try {
            doUnbindService();
        } catch (Throwable t) {
            Log.i("MainActivity", "Failed to unbind from the service", t);
        }
    }
}