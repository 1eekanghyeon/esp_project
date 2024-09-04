package com.example.esp;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    private Button newDeviceButton, existDeviceButton;
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate called");

        // 액션바 숨기기 코드를 제거합니다.
        // if (getSupportActionBar() != null) {
        //     getSupportActionBar().hide();
        //     Log.d(TAG, "Action bar hidden");
        // }

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
