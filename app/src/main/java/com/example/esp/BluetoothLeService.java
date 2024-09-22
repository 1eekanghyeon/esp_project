/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.example.esp;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.Manifest;
import android.content.pm.PackageManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import android.os.Handler;
import android.os.Looper;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;



/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 *
 * Bluetooth LE 기기와의 연결 및 통신을 관리하는 BluetoothLeService 클래스의 주요 기능을 설명합니다.
 * GATT 서버에 연결, 서비스 발견, 특성 읽기 및 알림 설정 등의 작업을 처리합니다.
 * 각 메서드와 콜백은 Bluetooth LE 프로토콜의 다양한 이벤트에 대응하며, 이러한 이벤트를 앱의 다른 부분에 알리기 위해 인텐트를 브로드캐스트
 */
public class BluetoothLeService extends Service {
    // 로그 태그, 블루투스 관리자, 어댑터, 장치 주소, GATT 클라이언트, 연결 상태
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private int mConnectionState = STATE_DISCONNECTED;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    // 연결 상태를 나타내는 상수
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    private boolean mConnected = false;  // BLE 연결 상태를 저장할 변수
    // 전역 변수 선언
    private List<byte[]> chunkList = new ArrayList<>();
    private int currentChunkIndex = 0;



    private static final UUID SERVICE_UUID = UUID.fromString(SampleGattAttributes.CUSTOM_RENAME_SERVICE);
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString(SampleGattAttributes.CUSTOM_RENAME_CHARACTERISTIC);
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // 인텐트 액션 정의
    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";

    // 샘플 GATT 특성 UUID
    public final static UUID UUID_HEART_RATE_MEASUREMENT =
            UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT);

    // 포어그라운드 서비스 관련 상수
    private static final String CHANNEL_ID = "BLEForegroundServiceChannel";

    // GATT 이벤트를 위한 콜백 메서드 구현
    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        @Override
        // 연결 상태 변경 시 처리 로직
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                mConnected = true;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                Log.d(TAG, "mConnected set to true");  // 디버그 메시지 추가
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());
                        mBluetoothGatt.requestMtu(512);  // 512로 설정);

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                mConnected = false;
                Log.i(TAG, "Disconnected from GATT server.");
                Log.d(TAG, "mConnected set to false");  // 디버그 메시지 추가

                disconnect();
                close();
                broadcastUpdate(intentAction);

                // 자동 재연결 시도, 5초 후에 재연결
//                new Handler(Looper.getMainLooper()).postDelayed(() -> {
//                    Log.i(TAG, "Attempting to reconnect...");
//                    connect(mBluetoothDeviceAddress);
//                }, 10000);  // 5초 후 재연결 시도
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "MTU changed to: " + mtu);
            } else {
                Log.e(TAG, "MTU change failed, status: " + status);
            }
        }


        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic write successful for chunk: " + currentChunkIndex);
                currentChunkIndex++;

                // 다음 청크 전송
                writeNextChunk();
            } else {
                Log.e(TAG, "Characteristic write failed with status: " + status);
            }
        }


        // 서비스 발견 시 처리 로직
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (BuildConfig.DEBUG) {
                    // GATT 서비스 리스트 탐색 (디버깅 시에만 출력)
                    for (BluetoothGattService gattService : gatt.getServices()) {
                        Log.d(TAG, "Service UUID: " + gattService.getUuid().toString());
                        for (BluetoothGattCharacteristic gattCharacteristic : gattService.getCharacteristics()) {
                            Log.d(TAG, "Characteristic UUID: " + gattCharacteristic.getUuid().toString());
                            for (BluetoothGattDescriptor descriptor : gattCharacteristic.getDescriptors()) {
                                Log.d(TAG, "Descriptor UUID: " + descriptor.getUuid().toString());
                            }
                        }
                    }
                }
                // 원래 코드대로 브로드캐스트 전송
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        // 특성 읽기 작업 완료 시 처리 로직
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        // 특성 변경(알림) 시 처리 로직
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };

    private final BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        Log.d(TAG, "Bluetooth is off, disconnecting BLE connection.");
                        disconnect();  // BLE 연결 해제
                        close();       // GATT 자원 해제
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.d(TAG, "Bluetooth is on.");
                        // 필요시 추가 로직
                        break;
                }
            }
        }
    };


    // 인텐트 액션에 따라 브로드캐스트 업데이트를 전송하는 메서드들

    // 단순 액션 브로드캐스트
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    // 데이터를 포함한 액션 브로드캐스트
    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            int flag = characteristic.getProperties();
            int format = -1;
            if ((flag & 0x01) != 0) {
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
                Log.d(TAG, "Heart rate format UINT16.");
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
                Log.d(TAG, "Heart rate format UINT8.");
            }
            final int heartRate = characteristic.getIntValue(format, 1);
            Log.d(TAG, String.format("Received heart rate: %d", heartRate));
            intent.putExtra(EXTRA_DATA, String.valueOf(heartRate));
        } else {
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for (byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
            }
        }
        sendBroadcast(intent);
    }

    // 서비스 바인딩을 위한 내부 클래스 및 바인더
    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        close();
        return true;  // Rebind 가능하도록 설정
    }

    private final IBinder mBinder = new LocalBinder();

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
        Log.d(TAG, "Service rebinded");
    }

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    @SuppressLint("MissingPermission")
    public boolean initialize() {
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }
    @Override
    public void onCreate() {
        super.onCreate();
        // Bluetooth 상태 변화 감지를 위한 BroadcastReceiver 등록
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBluetoothReceiver, filter);
    }


    @SuppressLint("MissingPermission")
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Android 12 (API 31) 이상에서는 추가 권한 체크 필요
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "BLUETOOTH_CONNECT permission is required.");
                return false;
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "BLUETOOTH_SCAN permission is required.");
                return false;
            }
        } else {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "ACCESS_FINE_LOCATION permission is required.");
                return false;
            }
        }

        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found. Unable to connect.");
            return false;
        }

        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    @SuppressLint("MissingPermission")
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    @SuppressLint("MissingPermission")
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    @SuppressLint("MissingPermission")
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD_UUID);
        if (descriptor != null) {
            if (enabled) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            } else {
                descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            }
            mBluetoothGatt.writeDescriptor(descriptor);
        }
    }

    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;
        return mBluetoothGatt.getServices();
    }

    @SuppressLint("MissingPermission")
    public void writeCharacteristic(String text) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }

        BluetoothGattCharacteristic characteristic =
                mBluetoothGatt.getService(UUID.fromString(SampleGattAttributes.CUSTOM_RENAME_SERVICE))
                        .getCharacteristic(UUID.fromString(SampleGattAttributes.CUSTOM_RENAME_CHARACTERISTIC));
        if (characteristic == null) {
            Log.w(TAG, "Text characteristic not found");
            return;
        }

        if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) == 0) {
            Log.e(TAG, "Characteristic does not support write");
            return;
        }
        characteristic.setValue(text.getBytes(StandardCharsets.UTF_8));
        boolean status = mBluetoothGatt.writeCharacteristic(characteristic);
        Log.d(TAG, "Write status: " + status);
    }

    @SuppressLint("MissingPermission")
    public void sendHexArrayInChunks(String[][] hexArray) {
        if (mBluetoothGatt == null || mBluetoothAdapter == null) {
            Log.e(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return;
        }

        executorService.execute(() -> {
            BluetoothGattService service = mBluetoothGatt.getService(SERVICE_UUID);
            if (service == null) {
                Log.e(TAG, "Custom service not found.");
                return;
            }

            BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
            if (characteristic == null) {
                Log.e(TAG, "Custom characteristic not found.");
                return;
            }

            if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) == 0) {
                Log.e(TAG, "Characteristic does not support write.");
                return;
            }

            // 데이터 전송을 위한 청크 준비
            chunkList.clear(); // 기존 청크 리스트 초기화
            List<Byte> byteList = new ArrayList<>();
            for (String[] row : hexArray) {
                for (String hexValue : row) {
                    byte[] data = hexStringToByteArray(hexValue);
                    for (byte b : data) {
                        byteList.add(b);

                        // 600바이트 청크가 찼을 때
                        if (byteList.size() == 500) {
                            byte[] chunk = new byte[byteList.size()];
                            for (int i = 0; i < byteList.size(); i++) {
                                chunk[i] = byteList.get(i);
                            }
                            chunkList.add(chunk); // 청크 추가
                            byteList.clear(); // 청크 전송 후 초기화
                        }
                    }
                }
            }

            // 남은 바이트 처리
            if (!byteList.isEmpty()) {
                byte[] chunk = new byte[byteList.size()];
                for (int i = 0; i < byteList.size(); i++) {
                    chunk[i] = byteList.get(i);
                }
                chunkList.add(chunk); // 마지막 청크 추가
            }

            // 첫 번째 청크 전송 시작
            currentChunkIndex = 0;
            writeNextChunk();
        });
    }
    @SuppressLint("MissingPermission")
    private void writeNextChunk() {
        if (currentChunkIndex >= chunkList.size()) {
            Log.d(TAG, "모든 청크 전송 완료.");
            return;
        }

        BluetoothGattService service = mBluetoothGatt.getService(SERVICE_UUID);
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);

        byte[] chunk = chunkList.get(currentChunkIndex);
        characteristic.setValue(chunk);
        boolean status = mBluetoothGatt.writeCharacteristic(characteristic);

        Log.d(TAG, "Write status: " + status + " for chunk: " + Arrays.toString(chunk));
    }

    @SuppressLint("MissingPermission")
    public String getConnectedDeviceMac() {
        if (mBluetoothGatt != null && mBluetoothGatt.getDevice() != null) {
            return mBluetoothGatt.getDevice().getAddress();
        }
        return null;  // 연결된 장치가 없을 경우 null 반환
    }



    private byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    public boolean isConnected() {
        return mConnected;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundService();
        return START_STICKY;
    }

    private void startForegroundService() {

        // Android 13 (API 33) 이상에서는 POST_NOTIFICATIONS 권한 체크
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "POST_NOTIFICATIONS permission is required for foreground service.");
                return;  // 권한이 없으면 포어그라운드 서비스 시작 안 함
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "BLE Foreground Service",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }

        Intent notificationIntent = new Intent(this, DeviceControlActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("BLE 연결 유지 중")
                .setContentText("BLE 장치와의 연결이 유지되고 있습니다.")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);
    }

    @Override
    public void onDestroy() {
        stopForeground(true);  // 서비스 종료 시 포어그라운드 중지
        super.onDestroy();
        unregisterReceiver(mBluetoothReceiver);
        // BLE 연결 해제 및 자원 정리
        disconnect();
        close();
    }
}
