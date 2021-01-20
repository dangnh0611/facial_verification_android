package com.example.donelogin.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.core.app.ActivityCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.donelogin.ml.FaceAuthPipeline;
import com.example.donelogin.model.Account;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;

public class FaceLoginCameraActivity extends BaseCameraActivity {
    private FaceAuthPipeline faceAuthPipeline;

    private int verificationStatus = 1; // 1 : waiting, 2: fail, 0: success


    // variable associated with an account
    private String keyAlias = "";
    private String mfaCode = "";
    private int deviceId;
    private float[][] faceEmbeddings;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        mfaCode = extras.getString("mfa_code");
        deviceId = Integer.valueOf(extras.getString("device_id"));
        Log.d("LOGIN", "mfa_code = " + mfaCode + ", device_id = " + deviceId);

        // get account information: key alias, face embeddings
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
                faceEmbeddings = account.embeddings;
                // init face auth pipeline
                try {
                    faceAuthPipeline = new FaceAuthPipeline(getAssets(), faceEmbeddings);
                    Log.d("LOGIN", faceEmbeddings.length + "___" + faceEmbeddings.toString() + "___" + faceEmbeddings[0][124]);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.execute();

        // Start camera
        if (allPermissionsGranted()) {
            startCamera(); //start camera if permission has been granted by user
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

    }


    protected ImageAnalysis.Analyzer createFrameAnalyzer() {
        return new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy imageProxy) {
                /*
                 MAIN ANALYZE CODE GOES HERE
                */
                // setup transformation Matrix
                if (transformationMatrix == null) {
                    transformationMatrix = getPreviewTransformationMatrix(imageProxy, mPreviewView);
                    overlayView.setTransformationMatrix(transformationMatrix);
                }

                long start = System.currentTimeMillis();
                verificationStatus = faceAuthPipeline.verifyFaceAsync(imageProxy, overlayView);
                Log.d("LOGIN", "analyze status:" + verificationStatus);
                if (verificationStatus == 0) {
                    // success
                    sendFaceAuthRequest(true);

                } else if (verificationStatus == 2) {
                    // failed
                    sendFaceAuthRequest(false);
                } else {
                    // do nothing
                }
                long end = System.currentTimeMillis();
                Log.d("TIME", Double.toString(end - start) + " ms to process a frame, start analyzer new frame per " + (start - _temp_time));
                _temp_time = start;
            }
        };
    }


    private void sendFaceAuthRequest(boolean success) {
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
            } catch (JSONException e) {
                e.printStackTrace();
            }
            Intent faceLoginSucessIntent = new Intent(FaceLoginCameraActivity.this, FaceRegistrationStatusActivity.class);
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
                            faceLoginSucessIntent.putExtra("requestStatus", isRequestSuccess);
                            faceLoginSucessIntent.putExtra("requestMsg", requestMsg);
                            faceLoginSucessIntent.putExtra("type", "login");
                            startActivity(faceLoginSucessIntent);
                        }
                    }, new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            // TODO: Handle error
                            Log.d("LOGIN", "Volley Error: " + error.toString());
                            boolean isRequestSuccess = false;
                            String requestMsg = "An unexpected error occur!";
                            faceLoginSucessIntent.putExtra("requestStatus", isRequestSuccess);
                            faceLoginSucessIntent.putExtra("requestMsg", requestMsg);
                            faceLoginSucessIntent.putExtra("type", "login");
                            startActivity(faceLoginSucessIntent);
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