package com.example.donelogin.activity;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.core.app.ActivityCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.donelogin.ml.FaceRegistrationPipeline;
import com.example.donelogin.model.Account;
import com.example.donelogin.util.Security;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;

public class FaceRegistrationCameraActivity extends BaseCameraActivity {
    ArrayList<float[]> embeddings;
    long end = System.currentTimeMillis();
    private FaceRegistrationPipeline faceRegistrationPipeline;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Init face registration pipeline
        try {
            faceRegistrationPipeline = new FaceRegistrationPipeline(getAssets());
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Start camera when every thing done
        if (allPermissionsGranted()) {
            startCamera(); //start camera if permission has been granted by user
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    protected ImageAnalysis.Analyzer createFrameAnalyzer() {
        return new ImageAnalysis.Analyzer() {
            @RequiresApi(api = Build.VERSION_CODES.P)
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
                embeddings = faceRegistrationPipeline.detectFaceAsync(imageProxy, overlayView);
                if (embeddings != null) {
                    Log.d("REGIST", "Embedding list size: " + embeddings.size());
                    sendFaceRegistrationRequest(true);
                }
                long end = System.currentTimeMillis();
                Log.d("TIME", Double.toString(end - start) + " ms to process a frame, start analyzer new frame per " + (start - _temp_time));
                _temp_time = start;
            }
        };
    }


    @RequiresApi(api = Build.VERSION_CODES.P)
    private void sendFaceRegistrationRequest(boolean success) {
        Intent faceRegistrationSucessIntent = new Intent(FaceRegistrationCameraActivity.this, FaceCameraStatusActivity.class);
        Bundle extras = getIntent().getExtras();
        String deviceName = android.os.Build.MODEL;
        String deviceBrand = android.os.Build.MANUFACTURER;
        int deviceAPILevel = Build.VERSION.SDK_INT;
        String deviceOS = Build.VERSION.RELEASE;
        Log.d("REGIST", deviceName + "__" + deviceBrand + "__" + deviceOS + "__" + deviceAPILevel);

        // Generate key pair
        String keyAlias = Security.generateRandomString();
        String publicKeyBase64Str = "";
        String signatureBase64Str = "";
        String private_code = extras.getString("code");
        try {
            Security.generateKeyPair(keyAlias);
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            PublicKey publicKey =
                    keyStore.getCertificate(keyAlias).getPublicKey();

            PrivateKey privateKey = (PrivateKey) keyStore.getKey(keyAlias, null);

            // sign on private code
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey);
            signer.update(private_code.getBytes(StandardCharsets.UTF_8));
            signatureBase64Str = Base64.encodeToString(signer.sign(), 0);
            publicKeyBase64Str = Base64.encodeToString(publicKey.getEncoded(), 0);
        } catch (Exception e) {
            e.printStackTrace();
        }


        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
        // Get server URL and FCM TOKEN
        SharedPreferences sharedPreferences = getSharedPreferences("APP_CONFIG", Context.MODE_PRIVATE);
        String token = sharedPreferences.getString("FCM_TOKEN", "");
        String url = sharedPreferences.getString("SERVER_URL", "") + "/device_registration";
        Log.d("REGIST", "POST to " + url);

        JSONObject requestParam = new JSONObject();
        try {
            requestParam.put("code", private_code);
            requestParam.put("public_key", publicKeyBase64Str);
            requestParam.put("device_model", deviceBrand + " " + deviceName);
            requestParam.put("device_os", "Android " + deviceOS + " (API level " + deviceAPILevel + ")");
            requestParam.put("code_signature", signatureBase64Str);
            requestParam.put("fcm_token", token);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Log.d("REGIST", requestParam.toString());
        JsonObjectRequest faceRegistrationRequest = new JsonObjectRequest(Request.Method.POST, url, requestParam,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        boolean isRequestSuccess = false;
                        String requestMsg = "";
                        try {
                            Log.d("REGIST", "Volley Success Response: " + response.toString());
                            String status = response.getString("status");
                            if (status.equals("success")) {
                                isRequestSuccess = true;
                                requestMsg = "";
                                int deviceID = response.getInt("device_id");

                                // Save new account to local database
                                Account newAccount = new Account();
                                newAccount.username = extras.getString("username");
                                newAccount.email = extras.getString("email");
                                newAccount.device_id = deviceID;
                                newAccount.keyAlias = keyAlias;
                                float[][] _embeddings = new float[5][128];
                                for (int i = 0; i < 5; i++) {
                                    _embeddings[i] = embeddings.get(i);
                                }
                                newAccount.embeddings = _embeddings;

                                // insert new account to db
                                // this must done in another thread because of Room constrains
                                AsyncTask.execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        accountDao.insertAll(newAccount);
                                    }
                                });
                            } else {
                                isRequestSuccess = false;
                                requestMsg = response.getString("msg");
                            }
                        } catch (JSONException e) {
                            isRequestSuccess = false;
                            requestMsg = "An unexpected error occur!";
                            e.printStackTrace();
                        }
                        // show result activity
                        faceRegistrationSucessIntent.putExtra("requestStatus", isRequestSuccess);
                        faceRegistrationSucessIntent.putExtra("requestMsg", requestMsg);
                        faceRegistrationSucessIntent.putExtras(extras);
                        startActivity(faceRegistrationSucessIntent);
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                // TODO: Handle error
                Log.d("REGIST", "Volley Error: " + error.toString());
                boolean isRequestSuccess = false;
                String requestMsg = "An unexpected error occur!";
                // show result activity
                faceRegistrationSucessIntent.putExtra("requestStatus", isRequestSuccess);
                faceRegistrationSucessIntent.putExtra("requestMsg", requestMsg);
                faceRegistrationSucessIntent.putExtras(extras);
                startActivity(faceRegistrationSucessIntent);
            }
        });

        // Add the request to the RequestQueue.
        queue.add(faceRegistrationRequest);
    }

}