package com.example.donelogin.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.donelogin.R;

public class FaceCameraStatusActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.face_registration_status);
        Button nextBtn = findViewById(R.id.face_registration_success_next);
        TextView statusText = findViewById(R.id.face_registration_status);
        TextView msgText = findViewById(R.id.face_registration_msg);
        Bundle extras = getIntent().getExtras();
        boolean status = extras.getBoolean("requestStatus");
        String msg = extras.getString("requestMsg");
        String type = extras.getString("type", "registration");
        if (type.equals("login")) {
            if (status == false) {
                statusText.setText("LOGIN FAILED");
                statusText.setTextColor(Color.RED);
                msgText.setText("ERROR: " + msg);
                msgText.setTextColor(Color.RED);
            } else {
                statusText.setText("LOGIN SUCCESSFULLY!");
                statusText.setTextColor(Color.GREEN);
                msgText.setText("");
                msgText.setTextColor(Color.GREEN);
            }

        } else if (type.equals("registration")) {
            if (status == false) {
                statusText.setText("SETUP FAILED");
                statusText.setTextColor(Color.RED);
                msgText.setText("ERROR: " + msg);
                msgText.setTextColor(Color.RED);
            }
        }


        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent mainActivityIntent = new Intent(FaceCameraStatusActivity.this, MainActivity.class);
                startActivity(mainActivityIntent);
            }
        });
    }
}

