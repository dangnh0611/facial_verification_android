package com.example.donelogin.activity;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
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
import android.os.Build;
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
import com.example.donelogin.ml.FaceRegistrationPipeline;
import com.example.donelogin.model.Account;
import com.example.donelogin.model.AccountDao;
import com.example.donelogin.model.AppDatabase;
import com.example.donelogin.util.Security;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FaceRegistrationCameraActivity extends AppCompatActivity {

    private Executor executor = Executors.newSingleThreadExecutor();
//    private Executor executor= Executors.newFixedThreadPool(8);

    private OverlayView overlayView;
    private Matrix transformationMatrix=null;
    private FaceRegistrationPipeline faceRegistrationPipeline;

    AppDatabase db;

    private int REQUEST_CODE_PERMISSIONS = 1001;
    private final String[] REQUIRED_PERMISSIONS = new String[]{ "android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE" };

    PreviewView mPreviewView;
    ImageView captureImage;

    // test
    double _temp_time = 0;

    private boolean isRequestSuccess =false;
    private String requestMsg = "";

    ArrayList<float[]> embeddings;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.face_registration_camera);

        mPreviewView = findViewById(R.id.previewView);

        // Overlay View for bounding box drawing, ..
        overlayView = (OverlayView) findViewById( R.id.overlayView);

        // Necessary to keep the Overlay above the TextureView so that the boxes are visible.
        overlayView.setWillNotDraw( false );
        overlayView.setZOrderOnTop( true );

        db= Room.databaseBuilder(getApplicationContext(),
                AppDatabase.class, AppDatabase.DB_NAME).build();

        try {
            faceRegistrationPipeline =new FaceRegistrationPipeline(getAssets());
        } catch (IOException e) {
            e.printStackTrace();
        }

        if(allPermissionsGranted()){
            startCamera(); //start camera if permission has been granted by user
        } else{
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
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
                embeddings= faceRegistrationPipeline.detectFaceAsync(imageProxy, overlayView);
                if(embeddings!=null){
                    Log.d("WOW", "embedding list size "+ embeddings.size());
                    Intent faceRegistrationSucessIntent = new Intent(FaceRegistrationCameraActivity.this, FaceRegistrationStatusActivity.class);
                    Bundle extras = getIntent().getExtras();
                    String deviceName = android.os.Build.MODEL;
                    String deviceBrand = android.os.Build.MANUFACTURER;
                    int deviceAPILevel = Build.VERSION.SDK_INT;
                    String deviceOS = Build.VERSION.RELEASE;
                    Log.d("DETAIL", deviceName + "__" + deviceBrand + "__" + deviceOS + "__" + deviceAPILevel );

                    // Generate key pair
                    String keyAlias = Security.generateRandomString();
                    String publicKeyBase64Str="";
                    String signatureBase64Str="";
                    String code = extras.getString("code");
                    try {
                        Security.generateKeyPair(keyAlias);
                        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
                        keyStore.load(null);
                        PublicKey publicKey =
                                keyStore.getCertificate(keyAlias).getPublicKey();

                        PrivateKey privateKey = (PrivateKey) keyStore.getKey(keyAlias, null);

                        Signature signer = Signature.getInstance("SHA256withRSA");
                        signer.initSign(privateKey);
                        signer.update(code.getBytes(StandardCharsets.UTF_8));
                        signatureBase64Str = Base64.encodeToString(signer.sign(), 0);

                        publicKeyBase64Str = Base64.encodeToString(publicKey.getEncoded(), 0);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }


                    // get device FCM token
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
                                }
                            });

                    while(!task.isComplete()){}
                    String token = task.getResult();

                    // Instantiate the RequestQueue.
                    RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
                    SharedPreferences sharedPreferences = getSharedPreferences("APP_CONFIG", Context.MODE_PRIVATE);
                    String url = sharedPreferences.getString("SERVER_URL", "") + "/device_registration";
                    Log.d("REGIST", url);

                    JSONObject requestParam = new JSONObject();
                    try {
                        requestParam.put("code", code);
                        requestParam.put("public_key", publicKeyBase64Str);
                        requestParam.put("device_model", deviceBrand + " " + deviceName);
                        requestParam.put("device_os", "Android " + deviceOS + " (API level " + deviceAPILevel + ")" );
                        requestParam.put("code_signature", signatureBase64Str);
                        requestParam.put("fcm_token", token);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    Log.d("REGIST", requestParam.toString());
                    JsonObjectRequest faceRegistrationRequest = new JsonObjectRequest
                            (Request.Method.POST, url, requestParam, new Response.Listener<JSONObject>() {
                                @Override
                                public void onResponse(JSONObject response) {
                                    try {
                                        Log.d("REGIST", "success volley" + response.toString());
                                        String status = response.getString("status");
                                        if(status.equals("success")){
                                            isRequestSuccess = true;
                                            requestMsg = "";
                                            int deviceID = response.getInt("device_id");

                                            Account newAccount = new Account();
                                            newAccount.username = extras.getString("username");
                                            newAccount.email = extras.getString("email");
                                            newAccount.device_id = deviceID;
                                            newAccount.keyAlias = keyAlias;
                                            float[][] _embeddings = new float[5][128];
                                            for (int i =0; i<5; i++){
                                                _embeddings[i] = embeddings.get(i);
                                            }
                                            newAccount.embeddings = _embeddings;

                                            AccountDao accountDao = db.accountDao();
                                            AsyncTask.execute(new Runnable() {
                                                @Override
                                                public void run() {
                                                    accountDao.insertAll(newAccount);
                                                }
                                            });

                                        }
                                        else{
                                            isRequestSuccess = false;
                                            requestMsg = response.getString("msg");
                                        }
                                    } catch (JSONException e) {
                                        isRequestSuccess= false;
                                        requestMsg = "An unexpected error occur!";
                                        e.printStackTrace();
                                    }
                                    faceRegistrationSucessIntent.putExtra("requestStatus", isRequestSuccess);
                                    faceRegistrationSucessIntent.putExtra("requestMsg", requestMsg);
                                    faceRegistrationSucessIntent.putExtras(extras);
                                    startActivity(faceRegistrationSucessIntent);
                                }
                            }, new Response.ErrorListener() {
                                @Override
                                public void onErrorResponse(VolleyError error) {
                                    // TODO: Handle error
                                    Log.d("REGIST", "error volley" + error.toString() );
                                    isRequestSuccess= false;
                                    requestMsg = "An unexpected error occur!";
                                    faceRegistrationSucessIntent.putExtra("requestStatus", isRequestSuccess);
                                    faceRegistrationSucessIntent.putExtra("requestMsg", requestMsg);
                                    faceRegistrationSucessIntent.putExtras(extras);
                                    startActivity(faceRegistrationSucessIntent);
                                }
                            });

                    // Add the request to the RequestQueue.
                    queue.add(faceRegistrationRequest);
                }
                long end= System.currentTimeMillis();
                Log.d("ANALYZE_TIME", Double.toString(end-start) + " ms, start per "+ Double.toString(start- _temp_time));
                _temp_time= start;
            }
        });


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