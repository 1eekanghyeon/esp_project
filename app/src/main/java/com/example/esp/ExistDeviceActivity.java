package com.example.esp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;

public class ExistDeviceActivity extends Activity {

    private static final String TAG = "ExistDeviceActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exist_device);

        connectToExistingDevice();
    }

    private void connectToExistingDevice() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // '사용자 리스트' 컬렉션에서 이전에 연결한 기기 정보를 가져옴
        db.collection("사용자 리스트").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                for (DocumentSnapshot document : task.getResult()) {
                    String deviceName = document.getId();
                    String deviceAddress = document.getString("MAC");

                    if (deviceAddress != null) {
                        // DeviceControlActivity로 인텐트를 보내어 BLE 연결 시도
                        Intent intent = new Intent(ExistDeviceActivity.this, DeviceControlActivity.class);
                        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, deviceName);
                        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, deviceAddress);
                        intent.putExtra("isReconnecting", true);  // 재연결 플래그 추가
                        startActivityForResult(intent, 1);
                        return;
                    }
                }
                Toast.makeText(ExistDeviceActivity.this, "이전에 연결된 기기를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
                navigateToLayoutChoice();
            } else {
                Toast.makeText(ExistDeviceActivity.this, "기기 정보를 가져오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
                navigateToLayoutChoice();
            }
        });
    }

    // 연결 결과를 받아 처리
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                boolean isConnected = data.getBooleanExtra("isConnected", false);
                if (isConnected) {
                    Toast.makeText(this, "연결에 성공하였습니다.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "BLE 연결 실패", Toast.LENGTH_SHORT).show();
                }
                navigateToLayoutChoice();
            }
        }
    }

    private void navigateToLayoutChoice() {
        Intent intent = new Intent(ExistDeviceActivity.this, LayoutChoiceActivity.class);
        startActivity(intent);
        finish();
    }
}
