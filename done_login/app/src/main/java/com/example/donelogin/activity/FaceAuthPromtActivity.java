package com.example.donelogin.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.donelogin.R;
import com.example.donelogin.model.Account;
import com.example.donelogin.model.AccountDao;
import com.example.donelogin.model.AppDatabase;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;

public class FaceAuthPromtActivity extends AppCompatActivity {
    TextView nameTextView, emailTextView, timeTextView, locationTextView, ipTextView;
    Button acceptBtn, rejectBtn;
    private String name, email, location, time, ip;
    private String mfaCode;
    private String keyAlias;
    private  int deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.face_auth_promt);
        nameTextView = findViewById(R.id.face_auth_name);
        emailTextView = findViewById(R.id.face_auth_email);
        timeTextView = findViewById(R.id.face_auth_time);
        locationTextView = findViewById(R.id.face_auth_location);
        ipTextView = findViewById(R.id.face_auth_ip);
        acceptBtn = findViewById(R.id.face_auth_accept);
        rejectBtn = findViewById(R.id.face_auth_reject);

        Bundle extras = getIntent().getExtras();
        mfaCode = extras.getString("mfa_code");
        deviceId = Integer.valueOf(extras.getString("device_id"));
        time = extras.getString("request_at", "UNKOWN");
        location = extras.getString("location", "UNKNOWN");
        ip = extras.getString("ip", "UNKNOWN");
        Log.d("LOGIN", "mfa_code = " + mfaCode + ", device_id = " + deviceId);

        // create db access object (DAO) instance
        AppDatabase db= Room.databaseBuilder(getApplicationContext(),
                AppDatabase.class, AppDatabase.DB_NAME).build();
        AccountDao accountDao = db.accountDao();
        // get account information: key alias
        new AsyncTask<Void, Void, Account>() {
            @SuppressLint("StaticFieldLeak")
            @Override
            protected Account doInBackground(Void... voids) {
                Account account = accountDao.getAccountByDeviceId(deviceId);
                return account;
            }

            @Override
            protected void onPostExecute(Account account) {
                if (account == null) return;
                keyAlias = account.keyAlias;
                name = account.username;
                email = account.email;

                // update view
                nameTextView.setText(name);
                emailTextView.setText(email);
                timeTextView.setText(time);
                locationTextView.setText(location);
                ipTextView.setText(ip);
            }
        }.execute();




        acceptBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent faceAuthCameraIntent = new Intent(FaceAuthPromtActivity.this, FaceAuthCameraActivity.class);
                faceAuthCameraIntent.putExtras(extras);
                startActivity(faceAuthCameraIntent);
            }
        });

        rejectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendFaceAuthRequest(false, "Access request rejected by user");
            }
        });
    }


    private void sendFaceAuthRequest(boolean success, String msg) {
        String status = "fail";
        if (success) {
            status = "success";
        }

        try {
            // sign with private key from Android keystore
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(keyAlias, null);
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey);
            signer.update(mfaCode.getBytes(StandardCharsets.UTF_8));
            String signatureBase64Str = Base64.encodeToString(signer.sign(), 0);

            // Send HTTP request
            // Instantiate the RequestQueue.
            RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
            SharedPreferences sharedPreferences = getSharedPreferences("APP_CONFIG", Context.MODE_PRIVATE);
            String url = sharedPreferences.getString("SERVER_URL", "") + "/login_2fa";
            Log.d("LOGIN", url);

            JSONObject requestParam = new JSONObject();
            try {
                requestParam.put("mfa_code", mfaCode);
                requestParam.put("device_id", deviceId);
                requestParam.put("code_signature", signatureBase64Str);
                requestParam.put("status", status);
                requestParam.put("msg", msg);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            Intent faceAuthStatusIntent = new Intent(FaceAuthPromtActivity.this, FaceCameraStatusActivity.class);
            Log.d("LOGIN", requestParam.toString());
            JsonObjectRequest faceRegistrationRequest = new JsonObjectRequest
                    (Request.Method.POST, url, requestParam, new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            boolean isRequestSuccess = false;
                            String requestMsg = "";
                            try {
                                Log.d("LOGIN", "Volley Success Response: " + response.toString());
                                String status = response.getString("status");
                                if (status.equals("success")) {
                                    isRequestSuccess = true;
                                    requestMsg = response.getString("msg");
                                } else if (status.equals("fail")) {
                                    isRequestSuccess = false;
                                    requestMsg = response.getString("msg");
                                }
                            } catch (JSONException e) {
                                requestMsg = "An unexpected error occur!";
                                e.printStackTrace();
                            }
                            // show authentication result to user
                            faceAuthStatusIntent.putExtra("requestStatus", isRequestSuccess);
                            faceAuthStatusIntent.putExtra("requestMsg", requestMsg);
                            faceAuthStatusIntent.putExtra("type", "login");
                            startActivity(faceAuthStatusIntent);
                        }
                    }, new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            // TODO: Handle error
                            Log.d("LOGIN", "Volley Error: " + error.toString());
                            boolean isRequestSuccess = false;
                            String requestMsg = "An unexpected error occur!";
                            faceAuthStatusIntent.putExtra("requestStatus", isRequestSuccess);
                            faceAuthStatusIntent.putExtra("requestMsg", requestMsg);
                            faceAuthStatusIntent.putExtra("type", "login");
                            startActivity(faceAuthStatusIntent);
                        }
                    });

            // Add the request to the RequestQueue.
            queue.add(faceRegistrationRequest);

        } catch (Exception e) {
            e.printStackTrace();
            // ignore
        }
    }

}
