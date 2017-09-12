/*
 * Copyright (C) 2016 Jones Chi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.renesas.castscreendemo;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.display.DisplayManager;
import android.media.projection.MediaProjectionManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import static android.renesas.castscreendemo.Config.VIRTUAL_DISPLAY_TYPE_PRESENTATION;
import static android.renesas.castscreendemo.Config.VIRTUAL_DISPLAY_TYPE_SCREENCAST;


public class CastActivity extends Activity implements DisplayManager.DisplayListener {
    private static final String TAG = "CastActivity";

    private static final String PREF_COMMON = "common";
    private static final String PREF_KEY_PACKAGE_NAME = "input_receiver";
    private static final String PREF_KEY_ENCODER = "encoder";
    private static final String PREF_KEY_RECEIVER = "receiver";
    private static final String PREF_KEY_RESOLUTION = "resolution";
    private static final String PREF_KEY_BITRATE = "bitrate";
    private static final String PREF_KEY_DISPLAY_MODE = "display_mode";


    private static final int[][] RESOLUTION_OPTIONS = {
            {1280, 720, 320},
            {800, 480, 160}
    };

    private static final int[] BITRATE_OPTIONS = {
            6144000, // 6 Mbps
            4096000, // 4 Mbps
            2048000, // 2 Mbps
            1024000 // 1 Mbps
    };

    private static final int[] DISPLAY_MODE_OPTIONS = {
            VIRTUAL_DISPLAY_TYPE_SCREENCAST,
            VIRTUAL_DISPLAY_TYPE_PRESENTATION
    };


    private static final int REQUEST_MEDIA_PROJECTION = 100;
    private static final String STATE_RESULT_CODE = "result_code";
    private static final String STATE_RESULT_DATA = "result_data";
    private static final int REQUEST_VD_INTENT = 654;

    private Context mContext;
    private MediaProjectionManager mMediaProjectionManager;
    private Handler mHandler = new Handler(new HandlerCallback());
    private Messenger mMessenger = new Messenger(mHandler);
    private Messenger mServiceMessenger = null;
    private TextView mReceiverTextView;
    private ListView mDiscoverListView;
    private ArrayAdapter<String> mDiscoverAdapter;
    private HashMap<String, String> mDiscoverdMap;
    private String mSelectedFormat = Config.DEFAULT_VIDEO_FORMAT;
    private int mSelectedWidth = RESOLUTION_OPTIONS[0][0];
    private int mSelectedHeight = RESOLUTION_OPTIONS[0][1];
    private int mSelectedDpi = RESOLUTION_OPTIONS[0][2];
    private int mSelectedBitrate = BITRATE_OPTIONS[0];
    private int mSelectedDisplayMode = DISPLAY_MODE_OPTIONS[0];
    private String mSelectedEncoderName;
    private String mReceiverIp = "";
    private DiscoveryTask mDiscoveryTask;
    private int mResultCode;
    private Intent mResultData;
    private ArrayList<String> mMatchingEncoders;
    private VDCallback callback;
    private boolean isConnected=false;
    private DisplayManager mDisplayManager;
    private ListView mDisplayListView;
    private ArrayAdapter<String> mDisplayAdapter;
    private HashMap<String, String> mDisplayMap;

    @Override
    public void onDisplayAdded(int i) {
        updateDisplaysList();

    }

    @Override
    public void onDisplayRemoved(int i) {
        updateDisplaysList();
    }

    @Override
    public void onDisplayChanged(int i) {
        updateDisplaysList();
    }

    private void updateDisplaysList(){
        ArrayList<String> list=new ArrayList<>();
        for(Display d: mDisplayManager.getDisplays()){
            list.add("id: "+d.getDisplayId()+", " +d.getName()+", state="+d.getState()+" flags="+d.getFlags());
        }
        mDisplayAdapter.clear();
        mDisplayAdapter.addAll(list);
        mDisplayAdapter.notifyDataSetChanged();
    }


    private class HandlerCallback implements Handler.Callback {
        public boolean handleMessage(Message msg) {
            Log.d(TAG, "Handler got event, what: " + msg.what);
            return false;
        }
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.w(TAG, "Service connected, name: " + name);
            mServiceMessenger = new Messenger(service);
            try {
                Message msg = Message.obtain(null, Config.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mServiceMessenger.send(msg);
                Log.d(TAG, "Connected to service, send register client back");
                isConnected=true;

            } catch (RemoteException e) {
                Log.d(TAG, "Failed to send message back to service, e: " + e.toString());
                e.printStackTrace();
                isConnected=false;
            }finally {
                updateReceiverStatus();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w(TAG, "Service disconnected, name: " + name);
            mServiceMessenger = null;
            isConnected=false;
            updateReceiverStatus();
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cast);

        if (savedInstanceState != null) {
            mResultCode = savedInstanceState.getInt(STATE_RESULT_CODE);
            mResultData = savedInstanceState.getParcelable(STATE_RESULT_DATA);
        }

        mContext = this;
        mMediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mDisplayManager= (DisplayManager) getSystemService(DISPLAY_SERVICE);
        mDiscoverdMap = new HashMap<>();
        mDiscoverListView = (ListView) findViewById(R.id.discover_listview);
        mDiscoverAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1);
        mDiscoverAdapter.addAll(mDiscoverdMap.keySet());
        mDiscoverListView.setAdapter(mDiscoverAdapter);
        mDiscoverListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                String name = mDiscoverAdapter.getItem(i);
                String ip = mDiscoverdMap.get(name);
                Log.d(TAG, "Select receiver name: " + name + ", ip: " + ip);
                mReceiverIp = ip;
                updateReceiverStatus();
                mContext.getSharedPreferences(PREF_COMMON, 0).edit().putString(PREF_KEY_RECEIVER, mReceiverIp).apply();
            }
        });

        // add server mode option
        mDiscoverAdapter.add(mContext.getString(R.string.server_mode));
        mDiscoverdMap.put(mContext.getString(R.string.server_mode), "");

        mDisplayListView = (ListView) findViewById(R.id.display_listview);
        mDisplayAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1);
        mDisplayListView.setAdapter(mDisplayAdapter);


        mReceiverTextView = (TextView) findViewById(R.id.receiver_textview);
        /*final Button selectButton = (Button) findViewById(R.id.select_button);
        selectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (packageNameEditText.getText().length() > 0) {
                    mReceiverIp = packageNameEditText.getText().toString();
                    Log.d(TAG, "Using ip: " + mReceiverIp);
                    updateReceiverStatus();
                    mContext.getSharedPreferences(PREF_COMMON, 0).edit().putString(PREF_KEY_PACKAGE_NAME, mReceiverIp).apply();
                    mContext.getSharedPreferences(PREF_COMMON, 0).edit().putString(PREF_KEY_RECEIVER, mReceiverIp).apply();
                }
            }
        });*/

        setupEncoderSpinner();

        Spinner resolutionSpinner = (Spinner) findViewById(R.id.resolution_spinner);
        ArrayAdapter<CharSequence> resolutionAdapter = ArrayAdapter.createFromResource(this,
                R.array.resolution_options, android.R.layout.simple_spinner_item);
        resolutionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        resolutionSpinner.setAdapter(resolutionAdapter);
        resolutionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                mSelectedWidth = RESOLUTION_OPTIONS[i][0];
                mSelectedHeight = RESOLUTION_OPTIONS[i][1];
                mSelectedDpi = RESOLUTION_OPTIONS[i][2];
                mContext.getSharedPreferences(PREF_COMMON, 0).edit().putInt(PREF_KEY_RESOLUTION, i).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                mSelectedWidth = RESOLUTION_OPTIONS[0][0];
                mSelectedHeight = RESOLUTION_OPTIONS[0][1];
                mSelectedDpi = RESOLUTION_OPTIONS[0][2];
                mContext.getSharedPreferences(PREF_COMMON, 0).edit().putInt(PREF_KEY_RESOLUTION, 0).apply();
            }
        });
        resolutionSpinner.setSelection(mContext.getSharedPreferences(PREF_COMMON, 0).getInt(PREF_KEY_RESOLUTION, 0));

        Spinner bitrateSpinner = (Spinner) findViewById(R.id.bitrate_spinner);
        ArrayAdapter<CharSequence> bitrateAdapter = ArrayAdapter.createFromResource(this,
                R.array.bitrate_options, android.R.layout.simple_spinner_item);
        bitrateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        bitrateSpinner.setAdapter(bitrateAdapter);
        bitrateSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                mSelectedBitrate = BITRATE_OPTIONS[i];
                mContext.getSharedPreferences(PREF_COMMON, 0).edit().putInt(PREF_KEY_BITRATE, i).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                mSelectedBitrate = BITRATE_OPTIONS[0];
                mContext.getSharedPreferences(PREF_COMMON, 0).edit().putInt(PREF_KEY_BITRATE, 0).apply();
            }
        });
        bitrateSpinner.setSelection(mContext.getSharedPreferences(PREF_COMMON, 0).getInt(PREF_KEY_BITRATE, 0));
        setupDisplayModeSpinner();


        mReceiverIp = mContext.getSharedPreferences(PREF_COMMON, 0).getString(PREF_KEY_RECEIVER, "");
        updateReceiverStatus();
        //startService();
    }

    private void setupDisplayModeSpinner(){
        Spinner displayMode = (Spinner) findViewById(R.id.display_mode_spinner);
        ArrayAdapter<CharSequence> bitrateAdapter = ArrayAdapter.createFromResource(this,
                R.array.display_mode_options, android.R.layout.simple_spinner_item);
        bitrateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        displayMode.setAdapter(bitrateAdapter);
        displayMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                mSelectedDisplayMode = DISPLAY_MODE_OPTIONS[i];
                mContext.getSharedPreferences(PREF_COMMON, 0).edit().putInt(PREF_KEY_DISPLAY_MODE, i).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                mSelectedDisplayMode = DISPLAY_MODE_OPTIONS[0];
                mContext.getSharedPreferences(PREF_COMMON, 0).edit().putInt(PREF_KEY_BITRATE, 0).apply();
            }
        });
        //displayMode.setSelection(mContext.getSharedPreferences(PREF_COMMON, 0).getInt(PREF_KEY_DISPLAY_MODE, 0));
        displayMode.setSelection(1);
    }
    private void setupEncoderSpinner(){
        Spinner encoderSpinner = (Spinner) findViewById(R.id.encoder_spinner);

        mMatchingEncoders=Utils.getCodecs(mSelectedFormat);
        if(mMatchingEncoders==null || mMatchingEncoders.size()==0){
            Log.e(TAG, "No matching encoders found");
            return;
        }

        ArrayAdapter<String> encoderAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,android.R.id.text1,  mMatchingEncoders);
        encoderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        encoderSpinner.setAdapter(encoderAdapter);
        encoderSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                mSelectedEncoderName = mMatchingEncoders.get(i);
                mContext.getSharedPreferences(PREF_COMMON, 0).edit().putInt(PREF_KEY_ENCODER, i).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                mSelectedEncoderName = mMatchingEncoders.get(0);
                mContext.getSharedPreferences(PREF_COMMON, 0).edit().putInt(PREF_KEY_ENCODER, 0).apply();
            }
        });
        encoderSpinner.setSelection(mContext.getSharedPreferences(PREF_COMMON, 0).getInt(PREF_KEY_ENCODER, 0));
    }

    @Override
    public void onResume() {
        super.onResume();

        // start discovery task
        mDiscoveryTask = new DiscoveryTask();
        mDiscoveryTask.execute();
        mDisplayManager.registerDisplayListener(this, new Handler());

    }

    @Override
    public void onPause() {
        super.onPause();
        mDiscoveryTask.cancel(true);
        mDisplayManager.unregisterDisplayListener(this);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnbindService();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_start) {
            Log.d(TAG, "==== start ====");
            if (mReceiverIp != null) {
                isConnected=false;
                startCaptureScreen();
                //invalidateOptionsMenu();
            } else {
                Toast.makeText(mContext, R.string.no_receiver, Toast.LENGTH_SHORT).show();
                isConnected=false;
            }
            updateReceiverStatus();
            return true;
        } else if (id == R.id.action_stop) {
            Log.d(TAG, "==== stop ====");
            stopScreenCapture();
            //invalidateOptionsMenu();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            Log.d(TAG, "User cancelled");
            Toast.makeText(mContext, R.string.user_cancelled, Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(TAG, "Starting screen capture");
        mResultCode = resultCode;
        mResultData = data;
        if (requestCode == REQUEST_MEDIA_PROJECTION) {

            startCaptureScreen();
        }else if(requestCode == REQUEST_VD_INTENT){
            if(callback!=null) callback.intentMPReady(data,resultCode);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mResultData != null) {
            outState.putInt(STATE_RESULT_CODE, mResultCode);
            outState.putParcelable(STATE_RESULT_DATA, mResultData);
        }
    }

    private void updateReceiverStatus() {
        if (mReceiverIp.length() > 0) {
            mReceiverTextView.setText(String.format(mContext.getString(R.string.receiver), mReceiverIp));
        } else {
            mReceiverTextView.setText(R.string.no_receiver);
        }
        if(isConnected){
            mReceiverTextView.setBackgroundColor(
                    getColor(android.R.color.holo_green_light));
        } else {
            mReceiverTextView.setBackgroundColor(
                    getColor(android.R.color.background_light));
        }
        updateDisplaysList();
    }



    private void startCaptureScreen() {
        //if(mSelectedDisplayMode==Config.VIRTUAL_DISPLAY_TYPE_SCREENCAST){
            Log.d(TAG, "startCaptureScreen: SCREENCAST");
            if (mResultCode != 0 && mResultData != null) {
                startService();
            } else {
                Log.d(TAG, "Requesting confirmation");
                // This initiates a prompt dialog for the user to confirm screen projection.
                startActivityForResult(
                        mMediaProjectionManager.createScreenCaptureIntent(),
                        REQUEST_MEDIA_PROJECTION);
            }
        //}else {
            //Log.d(TAG, "startCaptureScreen: PRESENTATION");
            //startService();
        //}

    }
    public void getVirtualDisplayIntent(VDCallback callback){
        this.callback=callback;
        Log.d(TAG, "getVirtualDisplayIntent");
        if (mResultCode != 0 && mResultData != null) {
            Log.w(TAG, "intentMPReady"+callback);

            callback.intentMPReady(mResultData, mResultCode);
        } else {
            Log.d(TAG, "Requesting confirmation");
            // This initiates a prompt dialog for the user to confirm screen projection.
            startActivityForResult(
                    mMediaProjectionManager.createScreenCaptureIntent(),
                    REQUEST_VD_INTENT);
        }
    }

    private void stopScreenCapture() {
        if (mServiceMessenger == null) {
            return;
        }
        final Intent stopCastIntent = new Intent(Config.ACTION_STOP_CAST);
        sendBroadcast(stopCastIntent);
        doUnbindService();
        isConnected=false;
        updateReceiverStatus();
    }

    private void startService() {
        if (mReceiverIp != null) {
            Intent intent = new Intent(this, MyCastService.class);
            intent.putExtra(Config.EXTRA_RESULT_CODE, mResultCode);
            intent.putExtra(Config.EXTRA_RESULT_DATA, mResultData);
            intent.putExtra(Config.EXTRA_RECEIVER_IP, mReceiverIp);
            intent.putExtra(Config.EXTRA_VIDEO_FORMAT, mSelectedFormat);
            intent.putExtra(Config.EXTRA_SCREEN_WIDTH, mSelectedWidth);
            intent.putExtra(Config.EXTRA_SCREEN_HEIGHT, mSelectedHeight);
            intent.putExtra(Config.EXTRA_SCREEN_DPI, mSelectedDpi);
            intent.putExtra(Config.EXTRA_VIDEO_BITRATE, mSelectedBitrate);
            intent.putExtra(Config.EXTRA_VIRTUAL_DISPLAY_TYPE, mSelectedDisplayMode);
            intent.putExtra(Config.EXTRA_VIDEO_ENCODER_NAME, mSelectedEncoderName);


            Log.d(TAG, "===== start service =====");
            startService(intent);
            bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
        } else {
            Intent intent = new Intent(this, MyCastService.class);
            startService(intent);
            bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    private void doUnbindService() {
        if (mServiceMessenger != null) {
            try {
                Message msg = Message.obtain(null, Config.MSG_UNREGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mServiceMessenger.send(msg);
            } catch (RemoteException e) {
                Log.d(TAG, "Failed to send unregister message to service, e: " + e.toString());
                e.printStackTrace();
            }
            /*if(mServiceConnection!=null) {
                unbindService(mServiceConnection);
                mServiceConnection=null;
            }*/
        }
    }

    private class DiscoveryTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            try {
                DatagramSocket discoverUdpSocket = new DatagramSocket();
                Log.d(TAG, "Bind local port: " + discoverUdpSocket.getLocalPort());
                discoverUdpSocket.setSoTimeout(3000);
                byte[] buf = new byte[1024];
                while (true) {
                    if (!Utils.sendBroadcastMessage(mContext, discoverUdpSocket, Config.DISCOVER_PORT, Config.DISCOVER_MESSAGE)) {
                        Log.w(TAG, "Failed to send discovery message");
                    }
                    Arrays.fill(buf, (byte)0);
                    DatagramPacket receivePacket = new DatagramPacket(buf, buf.length);
                    try {
                        discoverUdpSocket.receive(receivePacket);
                        String ip = receivePacket.getAddress().getHostAddress();
                        //Log.d(TAG, "Receive discover response from " + ip + ", length: " + receivePacket.getLength());
                        if (receivePacket.getLength() > 9) {
                            String respMsg = new String(receivePacket.getData());
                            //Log.d(TAG, "Discover response message: " + respMsg);
                            try {
                                JSONObject json = new JSONObject(respMsg);
                                String name = json.getString("name");
                                String width = json.getString("width");
                                String height = json.getString("height");
                                mDiscoverdMap.put(name, ip);
                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        mDiscoverAdapter.clear();
                                        mDiscoverAdapter.addAll(mDiscoverdMap.keySet());
                                    }
                                });
                                //Log.d(TAG, "Got receiver name: " + name + ", ip: " + ip + ", width: " + width + ", height: " + height);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    } catch (SocketTimeoutException e) {
                    }

                    Thread.sleep(3000);
                }
            } catch (SocketException e) {
                Log.d(TAG, "Failed to create socket for discovery");
                e.printStackTrace();
            } catch (IOException e) {
                //e.printStackTrace();
            } catch (InterruptedException e) {
                //e.printStackTrace();
                //Log.w(TAG,"Interrupted exception");
            }
            return null;
        }
    }
    interface VDCallback {
        void intentMPReady(Intent intent, int data);
    }
}
