package com.example.esp;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class ExistDeviceActivity extends Activity {

    private static final String TAG = "ExistDeviceActivity";

    private BluetoothLeService mBluetoothLeService;
    private boolean mServiceBound = false;
    private String deviceName;
    private String deviceAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exist_device);

        // SharedPreferences에서 저장된 기기 정보 가져오기
        SharedPreferences sharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        deviceName = sharedPreferences.getString("deviceName", null);
        deviceAddress = sharedPreferences.getString("deviceMac", null);

        if (deviceAddress != null) {
            bindBluetoothService();  // BLE 서비스와 바인딩
        } else {
            Toast.makeText(this, "이전에 연결된 기기를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
            navigateToLayoutChoice();  // 기기가 없으면 다른 화면으로 이동
        }
    }

    // BluetoothLeService와 바인딩하는 메서드
    private void bindBluetoothService() {
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    // BluetoothLeService와의 연결을 관리하는 ServiceConnection
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            mServiceBound = true;
            connectToExistingDevice();  // BLE 기기와 연결 시도
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
            mServiceBound = false;
        }
    };

    // BLE 기기 연결 시도 메서드
    private void connectToExistingDevice() {
        if (mBluetoothLeService != null && deviceAddress != null) {
            boolean result = mBluetoothLeService.connect(deviceAddress);
            if (result) {
                Toast.makeText(this, "기기와 연결을 시도합니다.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "BLE 연결에 실패했습니다.", Toast.LENGTH_SHORT).show();
                navigateToLayoutChoice();
            }
        }
    }

    // 브로드캐스트 수신기: BluetoothLeService에서 보낸 연결 상태 업데이트
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                Toast.makeText(ExistDeviceActivity.this, "BLE 연결 성공", Toast.LENGTH_SHORT).show();
                navigateToLayoutChoice();  // 연결 성공 시 이동
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Toast.makeText(ExistDeviceActivity.this, "BLE 연결 끊김", Toast.LENGTH_SHORT).show();
            }
        }
    };

    // 다른 화면으로 이동하는 메서드
    private void navigateToLayoutChoice() {
        Intent intent = new Intent(ExistDeviceActivity.this, LayoutChoiceActivity.class);
        startActivity(intent);
    }

    // onResume에서 브로드캐스트 리시버 등록
    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    // onPause에서 브로드캐스트 리시버 해제
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    // onDestroy에서 서비스 연결 해제
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mServiceBound) {
            unbindService(mServiceConnection);  // 서비스 연결 해제
            mServiceBound = false;
        }
    }

    // BluetoothLeService의 브로드캐스트 필터 설정
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        return intentFilter;
    }
}
