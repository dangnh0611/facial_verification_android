package com.example.donelogin.activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import androidx.camera.core.ImageProxy;
import androidx.room.Room;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.donelogin.OverlayView;
import com.example.donelogin.R;
import com.example.donelogin.adapter.AccountAdapter;
import com.example.donelogin.ml.FaceLoginPipeline;
import com.example.donelogin.model.Account;
import com.example.donelogin.model.AccountDao;
import com.example.donelogin.model.AppDatabase;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FaceLoginCameraActivity extends AppCompatActivity {

    private Executor executor = Executors.newSingleThreadExecutor();
//    private Executor executor= Executors.newFixedThreadPool(8);

    private OverlayView overlayView;
    private Matrix transformationMatrix=null;
    private FaceLoginPipeline faceLoginPipeline;
    private int REQUEST_CODE_PERMISSIONS = 1001;
    private final String[] REQUIRED_PERMISSIONS = new String[]{ "android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE" };
    private int verificationStatus = 1; // 1 indicate waiting
    PreviewView mPreviewView;
    ImageView captureImage;
    double _temp_time = 0;
    private String keyAlias="";
    private String mfaCode = "";
    private int deviceId;
    private float[][] faceEmbeddings;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.face_registration_camera);

        mPreviewView = findViewById(R.id.previewView);

        // Overlay View for bounding box drawing, ..
        overlayView = (OverlayView) findViewById( R.id.overlayView);
        captureImage = findViewById(R.id.captureImg);

        // Necessary to keep the Overlay above the TextureView so that the boxes are visible.
        overlayView.setWillNotDraw( false );
        overlayView.setZOrderOnTop( true );

        if(allPermissionsGranted()){
            startCamera(); //start camera if permission has been granted by user
        } else{
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        Bundle extras = getIntent().getExtras();
        mfaCode = extras.getString("mfa_code");
        deviceId = Integer.valueOf(extras.getString("device_id")) ;
        Log.d("LOGIN", mfaCode + "___" + deviceId);

        AppDatabase db= Room.databaseBuilder(this.getApplicationContext(),
                AppDatabase.class, AppDatabase.DB_NAME).build();
        AccountDao accountDao = db.accountDao();
        new AsyncTask<Void, Void, Account>() {
            @SuppressLint("StaticFieldLeak")
            @Override
            protected Account doInBackground(Void... voids) {
                Account account = accountDao.getAccountByDeviceId(deviceId);
                return account;
            }
            @Override
            protected void onPostExecute (Account account){
                if(account==null) return;
                keyAlias = account.keyAlias;
                faceEmbeddings = account.embeddings;
                try {
                    faceLoginPipeline =new FaceLoginPipeline(getAssets(), faceEmbeddings);
                    Log.d("LOGIN", faceEmbeddings.length + "___" + faceEmbeddings.toString() + "___" + faceEmbeddings[0][124]);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.execute();
    }


    private void startCamera() {
        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    // Request new camera provider
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    // Bind Preview, Analyze, Capture usecases
                    bindPreview(cameraProvider);
                } catch (ExecutionException | InterruptedException e) {
                    // No errors need to be handled for this Future.
                    // This should never be reached.
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }


    @SuppressLint("RestrictedApi")
    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {

        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
//            .setTargetResolution(new Size(1200, 1600 ))
                .build();

//        mPreviewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        mPreviewView.setImplementationMode(PreviewView.ImplementationMode.PERFORMANCE);

        // Select FRONT camera
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
//                .setTargetResolution(new Size(1200, 1600))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(executor, new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy imageProxy) {
                /*
                 MAIN ANALYZE CODE GOES HERE
                */

                // setup transformation Matrix
                if (transformationMatrix == null){
                    transformationMatrix=getTransformationMatrix(imageProxy, mPreviewView);
                    overlayView.setTransformationMatrix(transformationMatrix);
                }
//                List<Face> faces = faceDetector.detectFace(imageProxy);
//                faceDetector.drawBoundingBox(overlayView, faces);
                long start= System.currentTimeMillis();
                verificationStatus = faceLoginPipeline.verifyFaceAsync(imageProxy, overlayView);

                Log.d("LOGIN", "analyze!" + verificationStatus);
                if(verificationStatus==0){
                    // success
                    sendLoginStatusRequest(true);

                }
                else if(verificationStatus == 2){
                    // failed
                    sendLoginStatusRequest(false);
                }
                else{}

                long end= System.currentTimeMillis();
                Log.d("ANALYZE_TIME", Double.toString(end-start) + " ms, start per "+ Double.toString(start- _temp_time));
                _temp_time= start;
            }
        });

        ImageCapture.Builder builder = new ImageCapture.Builder();

        //Vendor-Extensions (The CameraX extensions dependency in build.gradle)
//        HdrImageCaptureExtender hdrImageCaptureExtender = HdrImageCaptureExtender.create(builder);

        // Query if extension is available (optional).
//        if (hdrImageCaptureExtender.isExtensionAvailable(cameraSelector)) {
//            // Enable the extension if available.
//            hdrImageCaptureExtender.enableExtension(cameraSelector);
//        }

        preview.setSurfaceProvider(mPreviewView.getSurfaceProvider());

        Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, preview, imageAnalysis);

    }


    private void sendLoginStatusRequest(boolean success) {
        String status = "fail";
        if(success){
            status = "success";
        }
        try{
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            PublicKey publicKey =
                    keyStore.getCertificate(keyAlias).getPublicKey();

            PrivateKey privateKey = (PrivateKey) keyStore.getKey(keyAlias, null);

            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey);
            signer.update(mfaCode.getBytes(StandardCharsets.UTF_8));
            String signatureBase64Str = Base64.encodeToString(signer.sign(), 0);
            String publicKeyBase64Str = Base64.encodeToString(publicKey.getEncoded(), 0);

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
                            String requestMsg= "";
                            try {
                                Log.d("LOGIN", "success voley" + response.toString());
                                String status = response.getString("status");
                                if(status.equals("success")){
                                    isRequestSuccess = true;
                                    requestMsg = response.getString("msg");
                                }
                            } catch (JSONException e) {
                                requestMsg = "An unexpected error occur!";
                                e.printStackTrace();
                            }
                            faceLoginSucessIntent.putExtra("requestStatus", isRequestSuccess);
                            faceLoginSucessIntent.putExtra("requestMsg", requestMsg);
                            faceLoginSucessIntent.putExtra("type", "login");
                            startActivity(faceLoginSucessIntent);
                        }
                    }, new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            // TODO: Handle error
                            Log.d("LOGIN", "error volley" + error.toString() );
                            boolean isRequestSuccess= false;
                            String requestMsg = "An unexpected error occur!";
                            faceLoginSucessIntent.putExtra("requestStatus", isRequestSuccess);
                            faceLoginSucessIntent.putExtra("requestMsg", requestMsg);
                            faceLoginSucessIntent.putExtra("type", "login");
                            startActivity(faceLoginSucessIntent);
                        }
                    });

            // Add the request to the RequestQueue.
            queue.add(faceRegistrationRequest);

        }
        catch (Exception e){
            e.printStackTrace();
            // ignore
        }
    }

    private boolean allPermissionsGranted(){

        for(String permission : REQUIRED_PERMISSIONS){
            if(ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED){
                return false;
            }
        }
        return true;
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if(requestCode == REQUEST_CODE_PERMISSIONS){
            if(allPermissionsGranted()){
                startCamera();
            } else{
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                this.finish();
            }
        }
    }

    private Matrix getTransformationMatrix(ImageProxy imageProxy, PreviewView previewView) {
        Rect cropRect = imageProxy.getCropRect();
        int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
        Matrix matrix = new Matrix();
        // A float array of the source vertices (crop rect) in clockwise order.
//        float[] source = {
//                cropRect.left,
//                cropRect.top,
//                cropRect.right,
//                cropRect.top,
//                cropRect.right,
//                cropRect.bottom,
//                cropRect.left,
//                cropRect.bottom
//        };

        // Since image is rotated when convert from MediaImage to InputImage
        float[] source = {
                cropRect.bottom,
                0,
                0,
                0,
                0,
                cropRect.right,
                cropRect.bottom,
                cropRect.right
        };
        Log.d("SOURCE", cropRect.flattenToString());
        Log.d("SOURCE", Float.toString(cropRect.right) + "w___h" + Float.toString(cropRect.bottom));
        // A float array of the destination vertices in clockwise order.
        float[] destination = {
                0f,
                0f,
                previewView.getWidth(),
                0f,
                previewView.getWidth(),
                previewView.getHeight(),
                0f,
                previewView.getHeight()
        };
        Log.d("DESTINATION", Float.toString(previewView.getWidth()) + "w___h" + Float.toString(previewView.getHeight()));
        // The destination vertexes need to be shifted based on rotation degrees.
        // The rotation degree represents the clockwise rotation needed to correct
        // the image.

//        // Each vertex is represented by 2 float numbers in the vertices array.
//        int vertexSize = 2;
//        // The destination needs to be shifted 1 vertex for every 90° rotation.
//        int shiftOffset = rotationDegrees / 90 * vertexSize;
//        shiftOffset=6;
//        float[] tempArray = destination.clone();
//        for (int toIndex = 0; toIndex < source.length; toIndex++) {
//            int fromIndex = (toIndex + shiftOffset) % source.length;
//            destination[toIndex] = tempArray[fromIndex];
//        }
//
        matrix.setPolyToPoly(source, 0, destination, 0, 4);
        return matrix;

    }



}