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

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import java.util.ArrayList;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 * BLE 기기 검색을 담당합니다. 사용 가능한 BLE 기기를 찾고, 스캔 결과를 사용자에게 보여주는 UI 컴포넌트를 포함
 */
public class DeviceScanActivity extends ListActivity {
    private LeDeviceListAdapter mLeDeviceListAdapter; // BLE 장치 목록을 관리하는 어댑터입니다.
    private BluetoothAdapter mBluetoothAdapter; // 블루투스 어댑터를 관리합니다.
    private boolean mScanning; // 스캔 중인지 여부를 나타냅니다.
    private Handler mHandler; // 비동기 작업을 스케줄링하기 위한 핸들러입니다.

    private static final int REQUEST_ENABLE_BT = 1; // 블루투스 활성화 요청에 사용되는 요청 코드입니다.
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    /**
     * This block is for requesting permissions up to Android 12+
     * 블루투스 기능을 사용하기 전에 사용자로부터 필요한 권한을 얻기 위해 호출
     *
     */

    private static final int PERMISSIONS_REQUEST_CODE = 191; //권한 요청 코드
    //안드로이드 버전 12 미만에서 요구되는 위치 권한을 정의
    private static final String[] BLE_PERMISSIONS = new String[]{
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
    };
    //안드로이드 12(API 레벨 31) 이상에서 블루투스 및 위치 관련 권한을 정의
    @SuppressLint("InlinedApi")
    private static final String[] ANDROID_12_BLE_PERMISSIONS = new String[]{
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION
    };
    // 현재 안드로이드 버전을 확인하여 적절한 권한 요청을 실행 메서드
    public static void requestBlePermissions(Activity activity, int requestCode) {
        // 안드로이드 12 이상에서는 더 구체적인 블루투스 권한을 요청합니다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            ActivityCompat.requestPermissions(activity, ANDROID_12_BLE_PERMISSIONS, requestCode);
        else
            ActivityCompat.requestPermissions(activity, BLE_PERMISSIONS, requestCode);
    }

    // 액티비티가 생성될 때 실행되는 초기 설정을 담당
    // 초기 설정: UI 핸들러 초기화, 권한 요청, BLE 지원 확인, 블루투스 어댑터 초기화
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // 액티비티 생성 시 기본적으로 수행해야 하는 초기화 작업을 상위 클래스에 위임
        getActionBar().setTitle(R.string.title_devices); //액티비티의 액션바에 표시될 제목을 설정
        mHandler = new Handler(); //UI를 업데이트하기 위한 핸들러를 초기화 - 나중에 UI 스레드에서 작업을 수행하는 데 사용될 수 있습니다.

        requestBlePermissions(this, PERMISSIONS_REQUEST_CODE); // 필요한 블루투스 관련 권한을 사용자에게 요청

        // BLE를 지원하는지 확인
        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish(); // BLE를 지원하지 않으면 앱을 종료합니다.
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
    }
    // 여기서부터 onRequestPermissionsResult 추가됨
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 권한이 승인되었을 때 BLE 스캔 등의 작업을 실행할 수 있음
                Toast.makeText(this, "권한이 승인되었습니다.", Toast.LENGTH_SHORT).show();
            } else {
                // 권한이 거부되었을 때 앱을 종료할 수 있음
                Toast.makeText(this, "권한이 필요합니다. 앱이 종료됩니다.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
    // 여기까지 onRequestPermissionsResult 추가됨
    // 액티비티가 시작될 때 시스템에 의해 자동으로 호출되어 메뉴를 설정
    // 옵션 메뉴 생성: 스캔 중 상태에 따라 아이템 가시성 조정
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        if (!mScanning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(
                    R.layout.actionbar_indeterminate_progress);
        }
        return true;
    }

    // 사용자가 액티비티의 옵션 메뉴에서 아이템을 선택했을 때 실행
    // 옵션 아이템 선택 처리: 스캔 시작/중지
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                mLeDeviceListAdapter.clear();
                scanLeDevice(true);
                break;
            case R.id.menu_stop:
                scanLeDevice(false);
                break;
        }
        return true;
    }

    // 블루투스 활성화 확인, 어댑터 초기화 및 스캔 시작
    @SuppressLint("MissingPermission")
    @Override
    protected void onResume() {
        super.onResume();
        // 앱이 다시 활성화되면 스캔을 재개할 수 있습니다.
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            // 블루투스가 꺼져 있다면 사용자에게 블루투스를 켜달라는 요청을 합니다.
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        // Initializes list view adapter.
        mLeDeviceListAdapter = new LeDeviceListAdapter();
        setListAdapter(mLeDeviceListAdapter);
        scanLeDevice(true);
    }

    //사용자가 시스템의 블루투스 활성화 요청에 응답했을 때 호출
    // 블루투스 활성화 요청 결과 처리
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        // 사용자가 블루투스 활성화 요청을 거절했는지 확인합니다.
        // 사용자가 거절했다면, 현재 액티비티를 종료합니다.  앱이 종료
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    // 앱 일시정지 시 스캔 중지 및 장치 목록 초기화
    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false); // 앱이 백그라운드로 이동하면 스캔을 중지합니다.
        mLeDeviceListAdapter.clear();
    }

    // 리스트 아이템 클릭 처리: 선택된 장치 정보를 DeviceControlActivity로 전달
    @SuppressLint("MissingPermission")
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        // 사용자가 선택한 기기의 정보를 얻기
        final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
        if (device == null) return;// 기기가 null이 아닌 경우에만 다음 단계를 진행

        //DeviceControlActivity 인텐트를 생성하고, 기기 이름과 주소를 추가
        final Intent intent = new Intent(this, DeviceControlActivity.class);
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, device.getName());
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());

        //현재 스캔 중이라면 스캔을 중지
        if (mScanning) {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            mScanning = false;
        }
        // DeviceControlActivity를 시작하여 기기 제어 화면으로 전환
        startActivity(intent);
    }

    // BLE 기기 스캔을 제어하는 메서드
    // BLE 스캔 시작/중지: 지정된 시간 후 자동 중지 로직 포함
    @SuppressLint("MissingPermission")
    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // 정의된 스캔 기간(SCAN_PERIOD) 후 스캔을 자동으로 중지하기 위한 지연 실행 코드.
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @SuppressLint("MissingPermission")
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    invalidateOptionsMenu();  //메뉴 옵션을 갱신하여 스캔 상태를 반영.
                }
            }, SCAN_PERIOD);

            mScanning = true;
            // BLE 스캔을 시작합니다. 스캔 결과는 mLeScanCallback 에서 처리됩니다.
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        }
        else {// 스캔을 중지
            mScanning = false;
            // 앱이 백그라운드로 이동하면 스캔을 중지합니다.
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }

    // 스캔 결과를 보여주기 위한 리스트 어댑터
    // 장치 목록 관리 및 리스트 뷰에 장치 표시
    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices;
        private LayoutInflater mInflator;

        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<BluetoothDevice>();
            mInflator = DeviceScanActivity.this.getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device) {
            if(!mLeDevices.contains(device)) {
                mLeDevices.add(device);
            }
        }

        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            @SuppressLint("MissingPermission") final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText(R.string.unknown_device);
            viewHolder.deviceAddress.setText(device.getAddress());

            return view;
        }
    }

    // BLE 스캔 콜백: 스캔된 장치 처리
    // Device scan callback.
    // 스캔된 장치를 리스트 어댑터에 추가
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mLeDeviceListAdapter.addDevice(device);
                    mLeDeviceListAdapter.notifyDataSetChanged();
                }
            });
        }
    };
    // 리스트 뷰의 각 항목을 위한 뷰 홀더
    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }
}
