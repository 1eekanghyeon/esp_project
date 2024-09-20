package com.example.esp;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.Manifest;
import android.content.pm.PackageManager;

public class MainActivity extends AppCompatActivity {

    private Button newDeviceButton, existDeviceButton;
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1;  // 권한 요청 코드

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate called");

        // POST_NOTIFICATIONS 권한 요청
        requestPostNotificationsPermission();

        // BLE 권한 요청
        requestBluetoothPermissions();

        // 레이아웃에서 버튼 가져오기
        newDeviceButton = findViewById(R.id.button_NewDevice);
        existDeviceButton = findViewById(R.id.button_ExistDevice);

        Log.d(TAG, "Buttons initialized");

        // 새로운 기기 연결 버튼 리스너
        newDeviceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "New Device button clicked");
                // DeviceScanActivity로 이동
                Intent intent = new Intent(MainActivity.this, DeviceScanActivity.class);
                startActivity(intent);
                Log.d(TAG, "Starting DeviceScanActivity");
            }
        });

        // 기존 기기 연결 버튼 리스너
        existDeviceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Exist Device button clicked");
                // ExistDeviceActivity로 이동
                Intent intent = new Intent(MainActivity.this, ExistDeviceActivity.class);
                startActivity(intent);
                Log.d(TAG, "Starting ExistDeviceActivity");
            }
        });
    }

    // POST_NOTIFICATIONS 권한 요청 메서드
    private void requestPostNotificationsPermission() {
        // Android 13 (API 33) 이상에서만 권한 요청 필요
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // 권한이 없으면 사용자에게 권한 요청
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST_CODE);
            } else {
                Log.d(TAG, "POST_NOTIFICATIONS 권한이 이미 부여되었습니다.");
            }
        }
    }

    // BLE 관련 권한 요청 메서드
    private void requestBluetoothPermissions() {
        // Android 12 (API 31) 이상에서만 권한 요청 필요
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                // 권한이 없으면 사용자에게 권한 요청
                requestPermissions(new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                }, PERMISSION_REQUEST_CODE);
            } else {
                Log.d(TAG, "Bluetooth 관련 권한이 이미 부여되었습니다.");
            }
        }
    }

    // 권한 요청 결과 처리
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, permissions[i] + " 권한이 허용되었습니다.");
                } else {
                    Log.e(TAG, permissions[i] + " 권한이 거부되었습니다.");
                }
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart called");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause called");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop called");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called");
    }
}
