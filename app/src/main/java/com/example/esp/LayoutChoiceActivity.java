package com.example.esp;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class LayoutChoiceActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_layout_choice);

        // 액션바
        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle("레이아웃 선택");

        Button button_layout1 = findViewById(R.id.button_layout1);
        Button button_layout2 = findViewById(R.id.button_layout2);

        button_layout1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), Layout1EditActivity.class);
                startActivity(intent);
            }
        });

        button_layout2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), Layout2EditActivity.class);
                startActivity(intent);
            }
        });
    }
}
