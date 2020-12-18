package com.example.donelogin.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

import com.example.donelogin.R;
public class FaceRegistrationWarningActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.face_registration_warning);
        Button nextBtn = findViewById(R.id.face_registration_warning_next);

        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent faceRegistrationCameraIntent = new Intent(FaceRegistrationWarningActivity.this, FaceRegistrationCameraActivity.class);
                startActivity(faceRegistrationCameraIntent);
            }
        });
    }
}
