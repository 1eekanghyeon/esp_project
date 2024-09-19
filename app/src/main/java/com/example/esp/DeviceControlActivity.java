package com.example.esp;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import android.content.SharedPreferences;


public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    private ExpandableListView mGattServicesList;
    private EditText mEditTextDataToSend;
    private Button mButtonSendData;

    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    private FirebaseFirestore db;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            Log.d(TAG, "BluetoothLeService initialized"); // 디버깅 추가
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
            Log.d(TAG, "BluetoothLeService disconnected"); // 디버깅 추가
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                Log.d(TAG, "GATT connected"); // 디버깅 추가
                invalidateOptionsMenu();
                sendConnectionResult(true);
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                Log.d(TAG, "GATT disconnected"); // 디버깅 추가
                invalidateOptionsMenu();
                clearUI();
                sendConnectionResult(false);
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                Log.d(TAG, "GATT services discovered"); // 디버깅 추가
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                String data = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                Log.d(TAG, "Data available: " + data); // 디버깅 추가
                displayData(data);
            }
        }
    };

    private void sendConnectionResult(boolean isConnected) {
        if (getIntent().getBooleanExtra("isReconnecting", false)) {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("isConnected", isConnected);
            setResult(Activity.RESULT_OK, resultIntent);
            finish();
        }
    }

    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    if (mGattCharacteristics != null) {
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);
                        final int charaProp = characteristic.getProperties();
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            if (mNotifyCharacteristic != null) {
                                mBluetoothLeService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }
                            Log.d(TAG, "Reading characteristic"); // 디버깅 추가
                            mBluetoothLeService.readCharacteristic(characteristic);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            Log.d(TAG, "Setting notification for characteristic"); // 디버깅 추가
                            mNotifyCharacteristic = characteristic;
                            mBluetoothLeService.setCharacteristicNotification(
                                    characteristic, true);
                        }
                        return true;
                    }
                    return false;
                }
            };

    private void clearUI() {
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        mDataField.setText(R.string.no_data);
        Log.d(TAG, "UI cleared"); // 디버깅 추가
    }

    private void saveUserNameToSharedPreferences(String userName, String macAddress) {
        SharedPreferences sharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("userName", userName);  // 사용자 이름 저장
        editor.putString("deviceMac", macAddress);  // MAC 주소 저장
        editor.apply();  // 변경 사항 적용
    }


    private void saveDeviceInfoToFirestore(String userName, String macAddress) {
        String uuid = "0000ff01-0000-1000-8000-00805f9b34fb";
        Map<String, Object> deviceInfo = new HashMap<>();
        deviceInfo.put("MAC", macAddress);
        deviceInfo.put("UUID", uuid);

        // 사용자 이름을 SharedPreferences에 저장 (여기서 중요한 부분)
        saveUserNameToSharedPreferences(userName,macAddress);


        Log.d(TAG, "Saving device info to Firestore: " + deviceInfo); // 디버깅 추가

        db.collection("사용자 리스트")
                .document(userName)
                .set(deviceInfo)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Device info successfully saved"); // 디버깅 추가
                    Toast.makeText(DeviceControlActivity.this, "Device Info Saved", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(DeviceControlActivity.this, LayoutChoiceActivity.class);
                    startActivity(intent);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving device info", e); // 디버깅 추가
                    Toast.makeText(DeviceControlActivity.this, "Error saving device info", Toast.LENGTH_SHORT).show();
                });
    }

    private View.OnClickListener sendDataClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            String userName = mEditTextDataToSend.getText().toString();
            if (!userName.isEmpty()) {
                if (mBluetoothLeService != null) {
                    Log.d(TAG, "Sending data: " + userName); // 디버깅 추가
                    mBluetoothLeService.writeCharacteristic(userName);
                    if (mGattCharacteristics != null && !mGattCharacteristics.isEmpty()) {
                        BluetoothGattCharacteristic characteristic = mGattCharacteristics.get(0).get(0);
                        String uuid = characteristic.getUuid().toString();
                        saveDeviceInfoToFirestore(userName, mDeviceAddress);
                    }
                }
            } else {
                Log.d(TAG, "No user name entered"); // 디버깅 추가
                Toast.makeText(DeviceControlActivity.this, "Please enter user name", Toast.LENGTH_SHORT).show();
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Firebase 초기화 추가
        FirebaseApp.initializeApp(this);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        db = FirebaseFirestore.getInstance();

        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
        mGattServicesList.setOnChildClickListener(servicesListClickListner);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);
        mEditTextDataToSend = (EditText) findViewById(R.id.edit_text_data_to_send);
        mButtonSendData = (Button) findViewById(R.id.button_send_data);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        startService(gattServiceIntent);  // 서비스가 백그라운드에서 실행되도록 유지
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        mButtonSendData.setOnClickListener(sendDataClickListener);

        Log.d(TAG, "onCreate called"); // 디버깅 추가
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "onResume: Connect request result=" + result); // 디버깅 추가
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
        Log.d(TAG, "onPause called, receiver unregistered"); // 디버깅 추가
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
        Log.d(TAG, "onDestroy called, service unbound"); // 디버깅 추가
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        Log.d(TAG, "onCreateOptionsMenu called"); // 디버깅 추가
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                Log.d(TAG, "Connect menu item selected"); // 디버깅 추가
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                Log.d(TAG, "Disconnect menu item selected"); // 디버깅 추가
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
                Log.d(TAG, "Connection state updated: " + resourceId); // 디버깅 추가
            }
        });
    }

    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data);
            Log.d(TAG, "Displaying data: " + data); // 디버깅 추가
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

        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();

            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();

                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 },
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 }
        );
        mGattServicesList.setAdapter(gattServiceAdapter);
        Log.d(TAG, "GATT services displayed"); // 디버깅 추가
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
