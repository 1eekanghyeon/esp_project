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

import com.google.firebase.firestore.FirebaseFirestore;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 *
 * BLE 기기에 연결하고, GATT 서비스 및 특성을 표시하며, 기기로부터 데이터를 수신하는 사용자 인터페이스를 제공
 *
 * DeviceControlActivity가 사용자에게 BLE 기기와의 연결 상태, 수신된 데이터,
 * 그리고 기기가 지원하는 GATT 서비스와 특성을 표시하는 방법을 설명합니다.
 * 서비스와의 연결, 데이터 수신 및 처리, 그리고 사용자 인터페이스 업데이트와 관련된 중요한 로직을 포함하고 있습니다.
 * 이 액티비티는 BluetoothLeService와 긴밀하게 협력하여 Bluetooth LE API와의 상호작용을 처리
 */
public class DeviceControlActivity extends Activity {
    // 로그를 위한 태그, 디바이스 이름과 주소를 전달받는 데 사용되는 키
    private final static String TAG = DeviceControlActivity.class.getSimpleName();
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    // UI 컴포넌트와 연결 및 데이터 표시를 위한 변수들
    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    private ExpandableListView mGattServicesList;
    private EditText mEditTextDataToSend; //추가
    private Button mButtonSendData;

    // BLE 서비스와의 통신을 관리하는 BluetoothLeService 객체
    private BluetoothLeService mBluetoothLeService;
    // 찾아진 GATT 특성들의 리스트
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    // 연결 상태를 표시하는 플래그
    private boolean mConnected = false;
    // 알림을 위한 GATT 특성
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    // GATT 서비스와 특성을 표시하기 위한 키
    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    // Firestore 인스턴스 추가
    private FirebaseFirestore db;

    // Code to manage Service lifecycle.
    // 서비스와의 연결을 관리하는 ServiceConnection 객체
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        // 서비스에 연결되면 초기화하고 자동으로 기기에 연결을 시도
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

    // BLE 서비스로부터 다양한 이벤트를 처리하는 BroadcastReceiver
    // 연결 상태 변경, GATT 서비스 발견, 데이터 수신 등을 처리
    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
                sendConnectionResult(true);
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
                sendConnectionResult(false);
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    // 기존 기기 연결 시 결과를 반환하는 메서드 추가
    private void sendConnectionResult(boolean isConnected) {
        if (getIntent().getBooleanExtra("isReconnecting", false)) {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("isConnected", isConnected);
            setResult(Activity.RESULT_OK, resultIntent);
            finish();
        }
    }

    // GATT 서비스 리스트에서 특정 항목을 클릭했을 때의 동작을 정의하는 리스너
    // 특정 GATT 특성이 선택되었을 때 지원하는 기능(읽기, 알림 등)을 확인하고 작업 수행
    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.
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
                            // If there is an active notification on a characteristic, clear
                            // it first so it doesn't update the data field on the user interface.
                            if (mNotifyCharacteristic != null) {
                                mBluetoothLeService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }
                            mBluetoothLeService.readCharacteristic(characteristic);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
                            mBluetoothLeService.setCharacteristicNotification(
                                    characteristic, true);
                        }
                        return true;
                    }
                    return false;
                }
            };

    // UI를 초기 상태로 리셋
    private void clearUI() {
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        mDataField.setText(R.string.no_data);
    }

    // Firestore에 데이터 저장 메서드 추가
    private void saveDeviceInfoToFirestore(String userName, String macAddress) {

        String uuid = "0000ff01-0000-1000-8000-00805f9b34fb";

        Map<String, Object> deviceInfo = new HashMap<>();
        deviceInfo.put("MAC", macAddress);
        deviceInfo.put("UUID", uuid);

        db.collection("사용자 리스트")
                .document(userName)
                .set(deviceInfo)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(DeviceControlActivity.this, "Device Info Saved", Toast.LENGTH_SHORT).show();
                    // 레이아웃 초이스 화면으로 이동
                    Intent intent = new Intent(DeviceControlActivity.this, LayoutChoiceActivity.class);
                    startActivity(intent);
                })
                .addOnFailureListener(e -> Toast.makeText(DeviceControlActivity.this, "Error saving device info", Toast.LENGTH_SHORT).show());
    }

    // sendDataClickListener 수정
    private View.OnClickListener sendDataClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            String userName = mEditTextDataToSend.getText().toString();
            // 사용자가 입력한 텍스트가 있는지 확인
            if (!userName.isEmpty()) {
                // BluetoothLeService를 통해 텍스트 전송
                if (mBluetoothLeService != null) {
                    mBluetoothLeService.writeCharacteristic(userName);
                    // Firestore에 데이터 업로드
                    if (mGattCharacteristics != null && !mGattCharacteristics.isEmpty()) {
                        BluetoothGattCharacteristic characteristic = mGattCharacteristics.get(0).get(0);
                        String uuid = characteristic.getUuid().toString();
                        saveDeviceInfoToFirestore(userName, mDeviceAddress);
                    }
                }
            } else {
                // 사용자가 텍스트를 입력하지 않은 경우 알림
                Toast.makeText(DeviceControlActivity.this, "Please enter user name", Toast.LENGTH_SHORT).show();
            }
        }
    };

    // UI 초기화, 인텐트에서 기기 이름과 주소 추출, BluetoothLeService에 바인드
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Firestore 인스턴스 초기화
        db = FirebaseFirestore.getInstance();

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
        mGattServicesList.setOnChildClickListener(servicesListClickListner);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);
        //추가
        mEditTextDataToSend = (EditText) findViewById(R.id.edit_text_data_to_send);
        mButtonSendData = (Button) findViewById(R.id.button_send_data);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        // 클릭 리스너를 버튼에 설정 //추가
        mButtonSendData.setOnClickListener(sendDataClickListener);
    }

    // BroadcastReceiver 등록, BluetoothLeService에 연결 시도
    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }
    // BroadcastReceiver 등록 해제
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }
    // 서비스 바인딩 해제
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }
    // 옵션 메뉴 생성, 연결 상태에 따라 메뉴 아이템 가시성 조정
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
        return true;
    }
    // 옵션 아이템 선택 처리(연결, 연결 해제, 홈 버튼)
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

    // 연결 상태 텍스트 업데이트
    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    // 수신된 데이터 표시
    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data);
        }
    }

    // 지원되는 GATT 서비스/특성을 탐색하고 UI에 표시
    // GATT 서비스와 특성을 리스트뷰에 표시하기 위해 데이터 구조를 채움
    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return; // gattServices가 null이면 메서드 실행을 중단합니다. 즉, 탐색된 서비스가 없는 경우에는 아무 것도 하지 않습니다.

        // UUID를 저장할 변수와 알 수 없는 서비스/특성에 대한 기본 문자열을 정의합니다.
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);

        // UI에 표시될 GATT 서비스와 특성 정보를 저장할 데이터 구조를 초기화합니다.
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // 탐색된 모든 GATT 서비스를 순회합니다.
        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();

            // UUID를 사용하여 서비스 이름을 조회하고, 알려지지 않은 경우 기본 문자열을 사용합니다.
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            // 현재 서비스의 특성 정보를 저장할 리스트를 생성합니다.
            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // 현재 서비스의 모든 특성을 순회합니다.
            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();

                // UUID를 사용하여 특성 이름을 조회하고, 알려지지 않은 경우 기본 문자열을 사용합니다.
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        // ExpandableListView 에 표시될 어댑터를 생성하고 설정합니다. 이 어댑터는 서비스와 특성의 이름 및 UUID를 표시합니다.
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
        // 설정된 어댑터를 ExpandableListView에 적용하여 UI에 표시합니다.
        mGattServicesList.setAdapter(gattServiceAdapter);
    }

    // BLE 서비스로부터의 인텐트를 수신하기 위한 IntentFilter 생성
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}
