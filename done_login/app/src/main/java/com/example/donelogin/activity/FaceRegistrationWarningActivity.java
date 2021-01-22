package com.example.donelogin.activity;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.donelogin.R;

public class FaceRegistrationWarningActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.face_registration_warning);
        TextView usernameTextView = findViewById(R.id.face_registration_warning_username);
        TextView emailTextView = findViewById(R.id.face_registration_warning_email);
        Button nextBtn = findViewById(R.id.face_registration_warning_next);
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            String username = extras.getString("username");
            String email = extras.getString("email");
            usernameTextView.setText(username);
            usernameTextView.setTypeface(usernameTextView.getTypeface(), Typeface.BOLD);
            emailTextView.setText(email);
            emailTextView.setTypeface(emailTextView.getTypeface(), Typeface.BOLD);
        }

        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent faceRegistrationCameraIntent = new Intent(FaceRegistrationWarningActivity.this, FaceRegistrationCameraActivity.class);
                Bundle extras = getIntent().getExtras();
                faceRegistrationCameraIntent.putExtras(extras);
                startActivity(faceRegistrationCameraIntent);
            }
        });
    }
}
