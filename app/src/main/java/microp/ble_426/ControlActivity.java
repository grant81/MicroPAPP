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
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import microp.ble_426.wavIO;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.Buffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ControlActivity extends Activity {
    private final static String TAG = ControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private TextView mConnectionState;
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
    private int[][] char_cache = new int[1][2];
    private String soundBuffer;
    private ArrayList<Byte> soundBytes = new ArrayList<Byte>();
    private int dataCounter = 0;
    private StorageReference mStorageRef;
    private FirebaseAuth mAuth;

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
            int counter = 0;
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
                Log.d(TAG, "data Avilable");
                String extra_data = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                String[] temp = extra_data.split(";");
                int index = Integer.parseInt(temp[0]);
                counter++;

                switch (index) {
                    case 0:
                        displaySoundData(temp[1]);
                        break;
                    case -1:
                        displaySoundData(temp[1]);
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
//                soundBuffer = "bbac998f7c7d7e807b807f7f8084818587868589878b8c8c8c8c898b8c8a8f8e8f8e918f908e91909494949494908f91909295969396959694989594929795979899969a969b9c9e9ca29f9f9e9c9b9d9d9ea3a1a2a1a2a0a39f9e9ea3a2a3a4a5a6a5a5a9a9a6abadabadafabadaba9abaea9aeafaeacada9aba8aaaaaeabacabaeaaabacacababa9aaacadaeaeacaaadabafafb1abafaaadaeb3b0b5b1b0aeb0acaeaeafacb0b0afafb5b1afb3b1b1b0afaeb1acafb0afabb0afb3b2b6b2b8b4b5b2b5b3b3b5b5b2b1b4b2b5b6b8b7b5b6b5b4b4b9b6bab7bab5b9b6b6b3b6b4b2b5b6b5b6b8b9bababab9b8b7b9b8bbb9bbb7bbb8bbb8bcb9bebbbfbbbdb8bcb8beb9bbb8bdb9c1bebebdbbb8babab6bab8bababcb8b8b5b7b6babbbcbabebbbebfc0bec2bec1c1bfbcbcb9b8bab9bcbbbbbbbebabdbdbebbbdbab9bab9bcbebfbbbfbbbdbebebbc0bcbcbfbebbb9b7b4b7b7bbbdbfbbbcb9bababebdbebec1bec0c4c2c3c3c4c0bebab8b5bab9bbbfc4c0c5c4c1bcbbb8babdbdc0bebebabdbbc0bdc3c1c5c3c2bfc3bec0c3c3c0c5c2c1c5c4c1c3c3c1bebdbfbcbec0c1bfbfbbbebebcc0c3c4c1c5c4c6c3c8c6c4c3c2bfc3c3c0c6c5c1c1c3bfbfbfbebdc1c2c2c2c3c0c1c4c4c6c4c1bcc1bdc2c2c5c1c5c1c3c1c2bcbebec0bfc3c1c3c1c3c1c3c1bfbebcbabdbfbcc0c1bcbec1bdbfbebcbcc1bec4c4c5c2c4c1c2bfc3c1c0c2c3c0c1c1bec0c0c0c1c2c0c2c0c3c1c2c1c2c0c3c3c0c3c2c3c3c6c2c7c4c8c5c6c3c9c5c3c1c1bdbfc2c4c5c4c4c1c1bec0bfbfbdc3c1c2c4c6c2c7c4c7c6c9c6ccc8cdcccbc8cac6c6c5c5c7c6c8c6c7c4c6c4c9c6c8c8cbc7cbc9c8c8c8c4c5c6c3c6c6c7c4c6c3c5c2c6c4c7c6c6c4c8c4c5c8c9c5c8c4c4c4c5c1c2c4c4c2c6c7c5c5c8c5c6c4c4c1c5c5c7c5c9c6c3c4c5c3c5c7c2c5c3c3c1c5c2c1c1c4c2c6c8c6c5c7c2c3c5c1c2c4c2c0c6c3c2c4c4c0c5c3c4c5c7c2c3c1c2c1c3c1c1c0c0bec2c3c1c3c5c3c6c9c8c8c8c3c4c4c4c6c6c6c8cac6cacac8c6cbc8cccacbc9ccc7cacacbcbcbcccacbc7cac8c8c6c8c6c6c9c8c9c9c7c3c6c3c7c6c8c5c8c3c6c6c9c9cbcaccc9c7c7c4c6c4c7c5cac7ccc8ccc8c8c6c7c4c6c6c5c6c4c2c3c5c3c9c5c6c3c4c1c3c0c5c3c4c8c8c7c6cac7c7c6c7c4c2c4c4c7c6cacac6c5c4c2c4c8c4c8c6c5c3c6c3c5c4c6c4c7c8c9cac9c7c4c3c1c7c5c8c9cbc6c9c5c7c4c5c2c8c6cacbcbc9cdc8cbcccbcaccc9c9c9c7c8c8c6c4c7c7c9cacdc9cbc8c7c3c6c3c0c1c3c0c2c4c3c6c8c7c8cac6c7c5c7c6cacaccc9cac5c9c9c9c7cdc9c9cccbc6c7c6c3c6c5c3c5c5c1c6c7c6c6c8c4c5c3c5c6c9c8c7c7c7c6c7c6c5c3c1c2c2c1c1c1c2c3c5c4c7c5c8c7cbcac9c8cac5c7c9c9c7c6c3c2c4c2c6c7c6c3c8c6c7c4c6c3c7c6cccacbc6c7c4c5c2c6c6c9cccecbcdcdc9cbcbcac7c8c7c5c3c5c4c4c7c5c4c8c7c4c9c6c5c5c7c3c7c4c7c6c9c9cfcccfcdcec9c9c4c6c3c6c7c8c7cac9c9c7c8c6c6c7c9c8cdcbcdcaccc8c9c6c8c7c5c7c5c6c8cac8cccbc9c8c9c7cac7cac7cac6cbc8cdcbcac7c9c5c9cacacbc9c6c8c8c4c9c9c8c8cccacccbcecacccacac6c9c5c6c7c9c7cccac6c6c6c4c5c8c6c6c6c6c6c6c4c3c1c3c4c5c3c4c3c5c1c4c3c6c4cac7c8c5c3bfc3c1c1c4c6c6c7cac9c6c8c7c5c4c7c3c4c3c6c6cacac9c8cbc7c9cdc9cac9c7c3c7c4c8c5cac8c6c5c8c5c8cbcacbcecbcccfcbcacac8c5c9c8c9c9cac7c8c7cacacbcacdcbc9c5c7c2c2c3c3c2c5c6c6cbc9c9c8c8c4cac8c9c7ccc6c9c8cbcacdcbcdcbc8c5c7c7c6c9cbc7c5cac8c5c7c7c6c5cac9cccbcfcbcdcbcbc7c8c5c6c2c4c2c0c0bfbcc0c2c3c4c4c1c4c2c2c4c5c2c3c3c0c1c0c1c2c4c2c6c6c7c9cbc7c9c8c7c9ccc9cdcac9c6c6c1c6c3c8c8cac6c8c6c9c9cbcbcbc9cac6c7cbc7c8cbcbc5cbc7c8c5c8c4c8c4cac7cbc8cbc7ccc9c9cacbc9cbcccccbcbc9cbc7c6c5c9c6ccced0cfcfcbc9c8c6c9c8cccbcdc9cdcacccbcbc8c9c8cccccccccac8c7cac9c8c6c9c5c9cbcbcccccacaccc8cac8c9c5c7c7c8c6cac7c7c7c8c5c9c6c6c4c5c1c6c4c3c5c8c4c9cac8c8cbc8cbccc9cac9c9cacdc9c9c7c9c7c8cbcbc9cac7c5c5c3c3c6c5c6c7c7c6c9c8cbcecfced1cecccdccc7cbc9c6c8c6c3c4c4bfc1c1c2c5c8c7cbcac8c5c8c2c2c1c5c3c7c7c7c4c7c6c9cccdcccbc6c3c5c2c3c3c7c6c6c5c8c6c9c9cacac9c7cbcbc9c9cccfccd0cfcfcdd0cfcdcbc7cbc9cac7c9c7c4c4c4c4c5cbc7cac7c6c2c1c3c3c3c5c5c4c5c5c6c3c6c3c9c7c7c5c6c2c7c7c8c6cac6c5c5c8c4c3c6c4c3c7c9c6cac9c7c6c8c6c6c8cccad0cfccc9c8c4c3c3c2c1c0c3c3c4c5c6c3c5c2c4c4c6c3c9c6c8c6c9c3c8c5c7c4c9c5c8c4c5c1c1c4c4c6cbcbc8cccbcac9cac9cbc8cecccecccdcacac8c8c9c8c9cacbcccacdcbcbc9ccc9cdcbcfcbccc7cbc7cbc9cac7c7c5c4c6c7c9c7cbcbcccacdcacbc7cbc9c8c6cac6c8cbcdcdd2d1cecfcfccc9ccc7c8c8c9c6c9c6c4c5c8c6c8cac8c9cacacbcdcacac9c8c8cbc9cdcccbc9ccc9c6c4c6c3c4c7c8c6c9c5c7c9c8c4c9c6c4c5c7c5c5cacccbcdd0cecacecbcac9ccc8c7c8c9c8ccccc9cac9c7cccecbcecdc9cbcdcacdcccbcccfcbcdcacac7cacbcbcccfcdcbcfcdcbcaccc7c9c8cac6c9c7c6c4c7c5c4c7c4c7c5c6c6ccc9d0cfd0cbcdc9c9c8cbc8c7c8c6c5c7c7c6cac9c6cbccc9cdcfcacccecbcbccc8c8c8c6c8c8c5c7cac7cbcbcacbcec9cdcbcbcccfc9cbc9c6c4c9c7ccc9cac7cac5c5c4c5c2c3c5c7c6c8c7c8c8c8c5c6c6c2c6c5c7c7c9c6cac6c7c9ccc8cecdcbc8cbc7c9c9cecdcdcccbc8cacccbcfccccc9cbc8ccc8cccaccc8cbc8cbc8cccccdcbcbc8c7c8c5c8c8c8c5c8c6c5c4cacac8cbc9c7c5c6c4c4c1c6c6c5c9cbc8c9cac8c7c5c8c7cbcccdcacfcbcccdccc8c8c5c7c8c8cacacac8cbc9cccacdcacbc9cdc9cccccbc9cecacdcecbc9ccc8cacccac8cac6c3c5c3c3c6c8c9cccbc9c8c7c3c5c2c4c3c7c4c3c3c5c2c6c7c6c7c9c7cccecbcccbcbc9c8c9c7c5c5c6c3c3c2c3c5c6c6cac9cbcccfc9ccc9cac7cbc7c7c7c8c5c7c9c7c9cccac9cdcbcbcbcbc8cac8cac9cac7c8c7c9c7c9cac8cacbcacbcfcacdcccbcbcccbcdcccccacac9c8c5c9c8cbcdcdcbc8c6c4c9c7c9caccc7cacacbc9cac8c7c6c3c4c4c7c6cac9cbc8c7c7c6c4c8c9c6cac8c5c5c8c3c8c6c9c8ccc9c8c5c7c6c8cacac8c7c5c4c5c3c4c4c5c2c7c5c6c4c7c4c8c6cbc8c8c5c7c4c9c8cac6cac6cacbcecccdc8c9c6c7c7c9c6cbc8c9c9ccc8cac8cccccbcacac7c4c6c2c4c0c2c1c4c3c8c8c9cacdc9cbc7c8c5c4c1c4c3c3c6c5c7c8c7c8cacbc9cecccac9cac6c7c8c7c7c7c6c8c9c7cac9cacacdc9cccbcdcbcecacdcacfcccfcbccc7ccc7cbcbcccacdc9cacccbccc9cac5c4c2c5c2c6c7c6c5cac6c9c9c8c5c9c6cacbc8c5c7c3c6c9c7cacacac6c9c6c6c5cbc8c5c8c9c6cacbc8cac8c6c5c7c3c3c2c7c4c6cacac7cbcccccbcfcccac9cdc9c9cacec9cacccac5c8c6c6c9c7c7c7c5c2c9c5c9c9c8c6cac7cacbcdcbcbc9c9c6c7c8c4c6c7c5c8ccc9cacccacacecccecdcfcccdcccacacecdc9cdccc8c8cac7c6c5c8c9cdced0cfd1cecfcececacccacccdccc9cbc5c4c6c4c6c6c6c4c9c4c7c8c8c7cbcccccccbcacbc8c8c5c7c4c9c8ccc8c8c3c8c4c9c7cbc7cac8ccc8cccac9c6c8c5c4c6c4c4c5c5c3c6c4c4c4c7c5c7c5c9c5c7c5c8c5cac8c9c7c6c4c5c5c6c9c7c8c4c4c2c6c4c6c4c7c4c3c4c7c4c5c6c6c6c6c6c4c4c2c3c2c8c8cbcccbc8cbc9cbcdcbcac9c8c4c7c6c7c8cacdcdceccd2cfcdcdcdc9cccecdcfceccc9c9c7c7c3c8c6c8c7c9c5c9c3c7c8ccc9d0cdcccac9c6c9c9cacccac8c9c7c4c6c3c5c4c7c7c9c5cac6c9cbcccacfcacccccdcbccc8c7c9c8cbcacec9c7c6c9c6c9c8c7c6c6c2c6c6c2c3c5c1c5c7c4c6c5c2c4c7c2c4c4c2c0c3c4c5c6c6c7c6c4c1c6c3c6c9c7c6c9c6c7cac9c8c5c5c6c8c6cac9cac8c7c9c8c7c7cbcacbcccdcccbcdcbcac8cacac7cbcac8c7cac8c7cacbcac7c8c5c2c3c6c8c6cccccbc8c9c8c7c7c9cac9c8c9c7c6c6cac6cbcac8c6cac7cac9cdc8cacbcbcacbcececdceccc9c6c8c6c7c9c8c9cac9c7c4c0c6c4c7c7cac7c6c6c8c7c8cac9cac9c9c9c8c9c7c9c6c7c3c8c4cac7c9c7cbcccccdcfcbceccccc9cdcac8cbcacbcacdcccccbcccbcac9c8cbc9cdcecdcbcac9c7c9cacbcaccccc8c8c6c8c6cacacacbcecbcfd0cdccccc8c4c5c5c7c6cccac9c9c9c8c9c8c4c6c1c3c3c7c5c9c5c6c2c7c8cbcbcdc8c7c7c6c6c7c7c9c9cacaccc6c8cbcac9cccac8c8c6c7c9c9c9cbcbcacbccc9cccac9c9ccc8c8cbcbc7cbcdc9cbcdc9cbcecacccbc7c5c6c2c7c8cac9c7c4c4c1c5c7c6c8c8c4c7c8c6cacbcacccdc9cdcbcacacdc7ccc9cbc9cdc9cbc9cbcacfcecdcbcdc8cacdcecacdcbcbcbcccacccbcbc8cac5c4c4c8c3c9c5c6c3c6c1c6c2c5c5c9c7cccaccc9c8c7c7c5c6c8c6c9c7c7c5c5c3c5c3c6c5c5c4c8c5cbcacbc8c8c3c6c6c7c9c8c5c3c0c0c1c1c1c3bfbfbfc3c2c5c5c6c3c8c4c8c7c8c4c8c4c5c5c5c7c6c7c7c9c4c8c5c6c4c5c3c7c3c9c9c6c5c5c1c0c3c2c4c3c7c5c8c7c9c5cbc9cacccecacfcccdcecdcacac8c5c7c6c7c9c9c6c7c7c6c6c8c6c6c5c6c5c9c8c8cacbc7cbccc9caccc8c9cdcac9c9c6c5c7c6c8c9c8c5c7c5c6c4c6c2c5c4c9c8cbc6c6c4c8c6c9c7c5c2c4c1c4c6c5c7c6c5c8c8c5cac9c8c6c6c3c6c5c9c7c9cac8c6c9cac8c7c7c9c6c9cbc9c7c6c1c3c5c3c5c6c5c7cccbceccc9c5c7c5c6c6c7c5c5c5c6c6c6c5c6c5c4c3c5c4c5c7c7c6c7c6c2c5c3c5c5cbc8cccacac6c7c9c6c7cbcac5cbc8c9c8cbc7ccc8cccdd0cbcfcbcac8cbcbc9ccc8c7c6cbc7cecdcecbcdc9cccacecfcfd0cfcdcbcccac9cacac8cacacac9c8c5c8c5c8c8cbc8cbc9cdc9cdcbcfcbcfcbcec9cac7cdc8cdcdccc8cac6c8c8c9c9c7c6c5c3c0c5c1c4c3c5c4c6c3c9c8cac9cac6c6c3c1c4c4c6c5c5c2c5c3c7c4c7c4c8c5cbc7cbc7cac5cac8cbc9cecacecacbc9c9c5c9c7c7c9cac8cccbc7c8cac8c9cbc9c7c7c9cccccececbc6c9c6c8c9c9c6cbc7c6c7cac6cacdcbcac8c6c7cbc8cbc9cbc8cccbcac8cac6c9cccdd0d0cdcbcac8cbcbcdccc9c9c8c6c6c8c5c7c8c9c9cdcacdcdcdccd1ced0cfd0cbcecccdcccfcbc7cac7c4c5c9c5cbcacbc9cdc8c8c7cac6cacccbcacac4c5c6c3c4c5c4c4c4c6c7c5c4c8c3c6c5c7c5c9c6cac6c8c3c8c6cac8cdcacac7cac8c9c7cacac9c8cdc9c8cbc9c8c9cac8cac7c9c5c8c8cac8cdcccfcdd0cdceccd0cfcccacbc8c7cbcccacccacac9c8c7c7c8c8cbc8cbc9cbc8cac8cbc7c7c6c6c2c3c3c3c4c4c6c6c5c4c5c3c6c6c8c5c3c2c3c2c5c9c7c9c9cac9cbc9cbc9cbc9cac8cac7cbcacacccbc9c6c7c4cac7cdcbcec9cac7c9c5c7c8cac9cac8c6c8c8cac7c9c4c4c1c8c5c9c8cac7c8c5c7c7c4c5c4c3c1c5c4c8c7cac8cac7cbc7cac6c8c4c7c2c8c5c3c4c6c2c6c7c4c6c6c3c7c8c7c9c8c6cacac7c9c9c7c7c7c4c7c7c6c7c9c6c7cac9cbcecccbcbccc6cac8cbc9cdcccccacbcacacacccacac7cac6c8c9c9c7cbc9c7c8c9c7cccdcdd0cfcbc9cbc7c8c7c9c9cccccfcecdcbcecbcac8ccc8cbcecccccecbc8ccc9c6c6c9c5c5c5c5c2c6c6c4c2c6c3c4c6c6c4c3c1c3c4c3c6c6c3c5cac9c7c9c5c4c3c9c7c8c6c7c4c6c4c6c3c5c3c6c5c6c5c7c7c6c7c5c4c4c8c6c9cccbc8cdcacbcbcdc8c8c5c6c4c8c9c5c7c9c5c3c6c4c5c4c9c8c8c6c7c4c7c7c6c9c7c7c8c8c5c8c5c8c8c9c8c9c6c9c8c7c9cac8cbccc9cacac8c9cac9cbc9cbcccecccecacecbcdcccfcacac8c8c5c5c7c5c8c7c7c8c8c5c7c9c7cacbcdcacccac8c5cac6c7c9cbc7cbc9c7c7c6c3c4c6c4c6c6c6c9c8c6c9cac7c7c8c2c3c1c2c0c2bfc1c0c3c2c4c4c5c4c9c6c9cbcec9cdc9c6c5c5c5c7c8c5c9c6c6c6c7c4c6c4c6c4c7c6c4c5c7c2c5c8c5c3c9c4c6c7c7c4cac8cacecdc8c9c7c2c0c3c1c0c4c4c2c7c7c6cbccc9cdcdc9cdcecbcfcdcacacecacbcacac9cccfcecdc9c8c7cccbcfcecec9c6c6c9c8cbcecdcbcecccccfcdcccccdc8c9c8c8c7cdcccacdcbc8caccc8cbc9c8c8cac9cccccccacdcacac8c9c5c8c8c7c8ccc7c6c8c5c3c1c4c3c7c5c6c5c3c3c2c7c3c7c4c6c2c5c3c4c3c2c3c4c5c4c6c3c5c1c3c3c4c1c7c4c6c5c8c4c9c7cac7c9c5c9c5c4c2c2c0c1c3c5c5c3c5c4c5c5c7c4cac6c9c8c9c6ccc9cac8cac6c7c5cac7c6c5c9c5cacccfcccac6c7c4c5c9c9c9c9c8c5c5c4c7c7cbcccac7cac7cacccbc8c7c7c6c5c6c8c6c7cac8c7c5c8c6c7cacac7cac9c9cccbcac8c8c2c3c2c7c7cccacecccac8cdc9c8cbccc8c8c9c8c9c7c9c7c9c6c8c6cbc8c8c8c7c3c4c4c2c4c4c5c7cac6c6c4c1bec2c2c0c1c5c2c5c7c5c1c5c2c5c7c9c7cbc6c9c9c7c4c7c4c6c8c6c4c6c2c1c6c5c6c5c6c1c0c1c2c2c5c9c6c8c9c8c7cbc9cac8c8c3c4c4c9c8cccbcbc8c8c7cac9c6c8c5c1c5c7c5cac9c7c9cac7cccbcccacecdcecdcecacbcdc9cbcdcbc7cdcbcacacbc6c6c6cac9cbcbcdc9cac9cbc8cbcbcecacdcdcbc9c8c6c5c8c6cac9c7c6cac7cacbccc9c9c6c9c6c7c9c9c6c6c8c6c5c6c6c4c5c7c5c8c5c7c8c7c6cac6c7c9cac6cbc8cac6cac5c2c5c3c6c8c9c9c9c7c9c9c5c7c6c6c6c9c6cac7c8c7c9c5cac7c9c7c9c4c7c4c6c6c8c7cac8c9c8c8c9cbcac9cbcacac9cac6c6c4c7c5cacbcbc9cbc6c9cacbcdcdcdccceccd0cdd2cecdcacac6c6c7c6c7c6c6c7c8cacbcecdceccd0cfd1cfd1cbcccacdcbd0cccdccccc8cdcbc8cacbc9cccdcbcbcac7c7cccbcacccfcbc9cdcccacdcdcbcfcecdcecccaccc9cccbcbc6c7c4c7c4c8c9c6c5c5c1c4c6c5c9c8c4c6cac6cac9c9c5cac8c9c4c8c3c1c1c2c1c4c4c5c6c5c3c5c3c0c2c1c3c4c8c8cac9c8c5c2c5c1c4c6c8c3c9c7c9cacccacdcacccccdcacecbcdcbcacccccdccd1cdd0cbd0ccd0cccdcacdcacccbcccbcdcacccbc8c8c8c7c8c9c7cac9c9cbccc8cccbc9c5c9c6cac9cecccecacfcbcfcbcfcbcfcbcfcccdc9cbc8c9c8cacacbcacecdcfceccc9c6c5c4c6c6c9c9c9cacbcbc9ccccc8cac8c4c2c5c3c7c7c7c5c6c4c6c4c6c2c3c1c2bec2bfc2c1c7c4c8c6c9c4c7c3c5c2c3c3c8c5cac8c5c3c6c3c8cbc8c8c8c4c5c7c6c9c9c9cacdcbcccacbc8cbcccccbcdc8c5c9c7c5c5c6c3c3c0c3c7c8c6cbc8c6c5c8c5c4c3c6c5c7c9c7c4c5c4c2c7c9c7c6c9c6c6c8c9c7cccbc9cdcecccdd1cdcdcdccc8cbcbcac9cdc9c8cdcbcbcecfcbcac9c9c7cacac9c7c6c3c8c9c6ccccc8c4c5c2c3c3c6c4c7c7c9c7cac6c3c2c1bec1c3c1c4c4c1c4c5c3c6c9c6cacbc7c8c8c5c8ccc8cecdcbcacdc7cccac8cacec9cdcccbc8c9c9cacdced0cccfcacbc9cccacbcacbc9c8cbc9cccccbc8cac5c8c7c8c6c9c5cac8ccc9cbc8c7c5c6c5c7cacac9c8c8c8c7c7c8c7c8c9cfcccdcccdc8ccc9cccccbcac9c8cacbc9cecccdcbcecacecbd0cecfcbcdc8cac9cccacdcbcecbcbcac9c5c5c4c6c8c7c8c7c6c7cac9cdcdc9cbcbc6c5cbc7c8cacbc8cacacacbc8c7c7c9c6cbc9cac6c6c1c1c0c1c2c3c6c2c5c2c5c3c3c1c7c5c9cdccc9cac5c6c8c7c7c9c8c6c9c7c7c6c7c4c8c7cbcacfcbc9c6c2c4c7c5c4c2c5c4c7cacdc9ccc7cacacdcacfcbcdcdd1cdd1cccbc8c8c7cdcbc8cbcac7cacccac7c5c4c4c8cbcccccfcacbcdcdcdcfcbcaccc9c9cac9c5c9c6c7c9c9c4c8c6c7c7cac7c8c7c9c8c7c8c9c6c9c9c6c6c5c3c6c8c6c9c8c3c6c8c4c9cac6c6cac6c7c9c8c8cbc9cacacbc9cbcacac6cac6c7c8cbc8cbc7c7c6c5c1c4c4c1c3c3c2c0c3c1c3c3c4c2c6c6c3c3c2bfbec2c1c5c6c7cacacdcdcecbccc9c9c7cac8c6c8c6c5cbceccd2d0cbcccdc7cbcac8c5c8c4c6c5c9c6cacacac8c9c7c6c9c8cbcccbc8cac7c6c5c4c2c6c4c6c6c6c1c3c1c7c8cccbcfcbcecccdc8cbc7cccbcdcbcdc9cac9cacacacac9c8c6c7c6c7c8c8c4c7c4c7c9cccbcdcbc9c9c8c8c8c8cac9cac9c9c4c9c5c8c8c9c4c7c3c6c5c9c7c8c3c4c3c2c3c1c1bebebdc1c0c3c2c2c1c4c2c6c6c5c5c8c6c8cbc9cbc9c8c4c4c1c4c2c9c7cacaccc7cbc8c7c9c8c7cbcbc9ccccc8c9cac7cacacacbccc9cac7cac6cac9c9c5c9c4c6c7c8c6cbc8c9cbcccbc9cbc9c8c8c9cac8c7c8c8c5c8c9c5cacac9caccc8cccccecccecacac9cdcbcfceccc9cdc8ccd0d1d0d2cecaccc9cbcccecdd0cecfcfcecacccacac8cac6c8c9cbc9cdc6c7c9c6c9cac8c5c8c5c7c7c6c5c6c3c4c6c5c7c8c7c9c9c7cbcbc9c8c9c8c9c8cacbc8c9cbc9cbcbc9cbc9c7c8c8c5cac8cbcbcdc8cdc9cbc8cbc8c9c6c9c6c8c7c8c7c9c6c9c8c6c5c8c5c6cac9c7cac8c6c8c6c4c4c5c1c5c2c4c2c6c2c7c3c7c8cac7cdcacac8c9c5cbc7cbc9cbc6cac8cdcbcdccd0ccd0cececdcccac9c9c7cac8cbcacccacccacdc9ccccccc7cbc7cacacac9c8c9c8cbcacdc9cdcbcbcbcdc9cbcac8c8c5c4c1c5c3c6c2c7c7c9c8cfcbcbc7c9c6c6c4c7c7c7c9c7c7c5c5c4c2c1c7c4c7cbc9c5c8c5c4c7c7c7c6c6c5c6c5c9c9c9c7c8c5c8c5c8c6c5c3c8c5c7c7c6c4c4c2c8c9c9cbcdcacccecbcbc9c5c4c7c6c7c6c6c3c6c5c7c5c9c6c3c7c8c6c9ccc6c7c7c6c6cac9cbc8cacacccbcecccdcacdcdcecdcecac8c8c6c8cbcac8cdcacacacac6c6c4c6c5c7c7c7c8cbc9cd";
                    if (soundBytes.size() > 0) {
                        mDataFieldSound.setText(String.valueOf("Data receiving done\n"));
                        if (soundBytes.size() > 8000) {
                            byte[] audio = new byte[soundBytes.size()];
                            Byte[] sound = soundBytes.toArray(new Byte[soundBytes.size()]);
                            Log.d(TAG, "sound =" + sound.toString());
                            int j = 0;
                            for (Byte b : sound) {
                                audio[j] = b.byteValue();
                                j++;
                            }
                            mDataFieldSound.setText(String.valueOf("sending your lovely voice\n length:"+soundBytes.size()+" bytes long\n"));
                            Log.d(TAG, "data length-----------" + audio.length);
                            byte[] file = wavIO.wavGet(audio);
                            StorageReference riversRef = mStorageRef.child("Sound/track1.wav");
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
                        } else {
                            String pitchBuffer = "";
                            String rollBuffer = "";
                            mDataFieldSound.setText(String.valueOf("sending sweet pitch n roll\n"));
                            for (int i = 0; i < soundBytes.size(); i++) {
                                if (i < soundBytes.size() / 2) {
                                    pitchBuffer += soundBytes.get(i).intValue();
                                    pitchBuffer += ",";
                                } else {
                                    rollBuffer += soundBytes.get(i).intValue();
                                    rollBuffer += ",";
                                }
                            }
                            mDataFieldSound.setText(String.valueOf("sending sweet pitch n roll\n #of pitch: "+pitchBuffer.length()/2+"\n#of roll: "+rollBuffer.length()/2));

                            StorageReference pitchRef = mStorageRef.child("PitchRoll/pitch.txt");
                            StorageReference rollRef = mStorageRef.child("PitchRoll/roll.txt");
                            pitchRef.putBytes(pitchBuffer.getBytes())
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
                                            Log.e(TAG, "Failed to upload pitch");
                                        }
                                    });
                            rollRef.putBytes(rollBuffer.getBytes())
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
                                            Log.e(TAG, "Failed to upload roll");
                                        }
                                    });

                        }
                        mDataFieldSound.append(String.valueOf("done uploading!!\n"));
                        dataCounter = 0;
                        soundBytes.clear();
                    }
                }
            };

    private void clearUI() {
        mDataFieldSound.setText(R.string.no_data);
        mBluetoothLeService.close();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.service);
        // initial firebase storage
        mStorageRef = FirebaseStorage.getInstance().getReference();
        mAuth = FirebaseAuth.getInstance();

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataFieldSound = (TextView) findViewById(R.id.data_value_sound);
        mDataFieldSound.setMovementMethod(new ScrollingMovementMethod());
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
        if (dataThread == null) {
            dataThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (mGattCharacteristics == null || mGattCharacteristics.size() == 0) {
                            Thread.sleep(100);
                        }
                        BluetoothGattCharacteristic characteristic0 = mGattCharacteristics.get(char_cache[0][0]).get(char_cache[0][1]);
                        mBluetoothLeService.setCharacteristicNotification(characteristic0, true);
                        Thread.sleep(8);
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
        switch (item.getItemId()) {
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
            dataCounter++;

            Log.d(TAG, "data Counter: " + dataCounter);
            // soundBuffer += data;
            soundBytes.add(hexStringToByteArraySingle(data));
            mDataFieldSound.setText(String.valueOf("hold on receiving data\n current data: "+data));

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

        int i = 0, j;
        for (ArrayList<BluetoothGattCharacteristic> service : mGattCharacteristics) {
            j = 0;
            for (BluetoothGattCharacteristic gatt : service) {
                if (BluetoothLeService.sound_char_uuid.equals(gatt.getUuid())) {
                    char_cache[0][0] = i;
                    char_cache[0][1] = j;
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

    public byte hexStringToByteArraySingle(String s) {
        byte data;
        //Log.d(TAG, "singleString = " + s);
        data = (byte) ((Character.digit(s.charAt(0), 16) << 4)
                + Character.digit(s.charAt(1), 16));
        return data;
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

}
