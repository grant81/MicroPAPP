package microp.ble_426;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ControlActivity extends Activity{
    private final static String TAG = ControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private TextView mConnectionState;
    private TextView mDataFieldAcc;
    private TextView mDataFieldTemp;
    private TextView mDataFieldSound;
    private Button mButton;
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private Thread dataThread;
    private int[][] char_cache = new int[3][2];
    private String soundBuffer;

    private StorageReference mStorageRef;

    // Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                String extra_data = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                String[] temp = extra_data.split(";");
                int index = Integer.parseInt(temp[0]);
                switch (index){
                    case 0:
                        displaySoundData(temp[1]);
                        break;
                    case 1:
                        mDataFieldTemp.setText(String.valueOf("Temp: "+temp[1]));
                        break;
                    case 2:
                        mDataFieldAcc.setText(String.valueOf("Acc: "+temp[1]));
                        break;
                    case -1:
                        break;
                }
            }
        }
    };

    // Upload data to firebase storage
    private final View.OnClickListener buttonClickListener =
        new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                StorageReference riversRef = mStorageRef.child("Sound/track.txt");
                if(soundBuffer != null) {
                    byte[] file = soundBuffer.getBytes();
                    riversRef.putBytes(file)
                        .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                            @Override
                            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                                Uri downloadUrl = taskSnapshot.getDownloadUrl();
                                Log.e(TAG, "Url: " + downloadUrl);
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.e(TAG, "Failed to upload");
                            }
                        });
                }
            }
    };

    private void clearUI() {
        mDataFieldAcc.setText(R.string.no_data);
        mDataFieldTemp.setText(R.string.no_data);
        mDataFieldSound.setText(R.string.no_data);
        mBluetoothLeService.close();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.service);
        // initial firebase storage
        mStorageRef = FirebaseStorage.getInstance().getReference();

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataFieldAcc = (TextView) findViewById(R.id.data_value_acc);
        mDataFieldTemp = (TextView) findViewById(R.id.data_value_temp);
        mDataFieldSound = (TextView) findViewById(R.id.data_value_sound);
        soundBuffer = "";
        mButton = (Button) findViewById(R.id.send_button);
        mButton.setOnClickListener(buttonClickListener);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
        if (dataThread==null) {
            dataThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (mGattCharacteristics == null || mGattCharacteristics.size()==0){
                            Thread.sleep(200);
                        }
                        BluetoothGattCharacteristic characteristic0 = mGattCharacteristics.get(char_cache[0][0]).get(char_cache[0][1]);
                        mBluetoothLeService.setCharacteristicNotification(characteristic0, true);
                        Thread.sleep(100);
                        BluetoothGattCharacteristic characteristic1 = mGattCharacteristics.get(char_cache[1][0]).get(char_cache[1][1]);
                        mBluetoothLeService.readCharacteristic(characteristic1);
                        Thread.sleep(100);
                        BluetoothGattCharacteristic characteristic2 = mGattCharacteristics.get(char_cache[2][0]).get(char_cache[2][1]);
                        mBluetoothLeService.setCharacteristicNotification(characteristic2, true);
                        Thread.sleep(200);
                    } catch (Exception e) {
                        e.getLocalizedMessage();
                    }
                }
            });
            dataThread.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_service, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displaySoundData(String data) {
        if (data != null) {
            soundBuffer += data + " ";
//            Log.e(TAG, "Sound data: " + soundBuffer);
            mDataFieldSound.setText(String.valueOf("Sound Data\n"+soundBuffer));
        }
    }

    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        String LIST_NAME = "NAME";
        String LIST_UUID = "UUID";
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, GattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();
            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, GattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        int i=0,j;
        for (ArrayList<BluetoothGattCharacteristic> service : mGattCharacteristics) {
            j=0;
            for (BluetoothGattCharacteristic gatt : service){
                if (BluetoothLeService.sound_char_uuid.equals(gatt.getUuid())) {
                    char_cache[0][0]=i;
                    char_cache[0][1]=j;
                } else if (BluetoothLeService.temp_char_uuid.equals(gatt.getUuid())) {
                    char_cache[1][0]=i;
                    char_cache[1][1]=j;
                } else if (BluetoothLeService.acc_char_uuid.equals(gatt.getUuid())) {
                    char_cache[2][0]=i;
                    char_cache[2][1]=j;
                }
                j++;
            }
            i++;
        }

    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

}
