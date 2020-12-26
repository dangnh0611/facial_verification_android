package com.example.donelogin.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.room.Room;

import com.example.donelogin.R;
import com.example.donelogin.model.AppDatabase;
import com.example.donelogin.util.NukeSSLCerts;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.android.OpenCVLoader;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences sharedPreferences = getSharedPreferences("APP_CONFIG", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("SERVER_URL", "https://192.168.11.2:5000");
//        editor.putString("SERVER_URL", "https://192.168.43.200:5000");
        editor.apply();

        // get device FCM token
        Log.d("TOKEN", "Start getting fcm token");
        Task<String> task = FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        if (!task.isSuccessful()) {
                            Log.w("FCM", "Fetching FCM registration token failed", task.getException());
                            return;
                        }
                        // Get new FCM registration token
                        String _token = task.getResult();

                        // Log and toast
                        Log.d("FCM", _token);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                editor.putString("FCM_TOKEN", _token);
                                editor.apply();
                            }
                        });
                    }
                });

        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Trust all SSL certs
        NukeSSLCerts.nuke();

        FloatingActionButton fab = findViewById(R.id.fab);
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
            Toast.makeText( this, "Unable to load OpenCV", Toast.LENGTH_SHORT ).show();
        }

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                Intent cameraIntent = new Intent(MainActivity.this, CameraActivity.class);
//                startActivity(cameraIntent);
                scanQRCode();
            }
        });

        Bundle extras = getIntent().getExtras();
        if (extras==null) return;

        // if main activity start by a tap on pushed notification
        String notificationType = extras.getString("type", "");
        if(notificationType.equals("faceid")) {
            Intent faceLoginCameraIntent = new Intent(MainActivity.this, FaceLoginCameraActivity.class);
            faceLoginCameraIntent.putExtras(extras);
            startActivity(faceLoginCameraIntent);
        }
    }

    private void scanQRCode() {
        IntentIntegrator integrator = new IntentIntegrator(MainActivity.this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
        integrator.setCameraId(0);
        integrator.setPrompt("");
        integrator.setBeepEnabled(true);
        integrator.setBarcodeImageEnabled(true);
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if(result != null) {
            if(result.getContents() == null) {
                Toast.makeText(this, "Cancelled", Toast.LENGTH_LONG).show();
            } else {
                String qrText = result.getContents();
                try {
                    JSONObject jObject = new JSONObject(qrText);
                    String code = jObject.getString("code");
                    String id = jObject.getString("user_id");
                    String username = jObject.getString("username");
                    String email =jObject.getString("email");
                    Toast.makeText(this, "Scanned: " + qrText, Toast.LENGTH_LONG).show();
                    Intent faceRegistrationWarningIntent = new Intent(MainActivity.this, FaceRegistrationWarningActivity.class);
                    faceRegistrationWarningIntent.putExtra("code", code);
                    faceRegistrationWarningIntent.putExtra("user_id", id);
                    faceRegistrationWarningIntent.putExtra("username", username);
                    faceRegistrationWarningIntent.putExtra("email", email);
                    startActivity(faceRegistrationWarningIntent);
                } catch (JSONException e) {
                    Toast.makeText(this, "Invalid QR code!" + qrText, Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }

            }

        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}