package com.example.donelogin.activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.extensions.HdrImageCaptureExtender;
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
import com.example.donelogin.ml.FaceAuthPipeline;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class BaseCameraActivity extends AppCompatActivity {

    protected Executor executor = Executors.newSingleThreadExecutor();
//    private Executor executor= Executors.newFixedThreadPool(8);
    protected AppDatabase db;
    protected AccountDao accountDao;
    protected PreviewView mPreviewView;
    protected OverlayView overlayView;
    protected Matrix transformationMatrix=null;
    protected int REQUEST_CODE_PERMISSIONS = 1001;
    protected final String[] REQUIRED_PERMISSIONS = new String[]{ "android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE" };

    // temporary variable for performance evaluation
    protected  double  _temp_time = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.face_camera);
        mPreviewView = findViewById(R.id.previewView);

        // Overlay View for bounding box drawing, ..
        overlayView = (OverlayView) findViewById( R.id.overlayView);
        // Necessary to keep the Overlay above the TextureView so that the boxes are visible.
        overlayView.setWillNotDraw( false );
        overlayView.setZOrderOnTop( true );

        //
        db= Room.databaseBuilder(this.getApplicationContext(),
                AppDatabase.class, AppDatabase.DB_NAME).build();
        accountDao = db.accountDao();
    }


    protected void startCamera() {
        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    // Request new camera provider
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    // Bind Preview, Analyze, Capture usecases
                    bindPreview(cameraProvider, createFrameAnalyzer());
                } catch (ExecutionException | InterruptedException e) {
                    // No errors need to be handled for this Future.
                    // This should never be reached.
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }


    @SuppressLint("RestrictedApi")
    protected void  bindPreview(@NonNull ProcessCameraProvider cameraProvider, ImageAnalysis.Analyzer frameAnalyzer) {

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

        // image build analysis action
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
//                .setTargetResolution(new Size(1200, 1600))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(executor, frameAnalyzer);

        preview.setSurfaceProvider(mPreviewView.getSurfaceProvider());

        Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, preview, imageAnalysis);

    }


    protected ImageAnalysis.Analyzer createFrameAnalyzer(){
        return new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy image) {
                // do nothing
            }
        };
    }


    protected boolean allPermissionsGranted(){

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

    protected Matrix getPreviewTransformationMatrix(ImageProxy imageProxy, PreviewView previewView) {
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
        // The destination vertexes need to be shifted based on rotation degrees.
        // The rotation degree represents the clockwise rotation needed to correct
        // the image.

//        // Each vertex is represented by 2 float numbers in the vertices array.
//        int vertexSize = 2;
//        //The destination needs to be shifted 1 vertex for every 90° rotation.
//        int shiftOffset = rotationDegrees / 90 * vertexSize;
//        shiftOffset=6;
//        float[] tempArray = destination.clone();
//        for (int toIndex = 0; toIndex < source.length; toIndex++) {
//            int fromIndex = (toIndex + shiftOffset) % source.length;
//            destination[toIndex] = tempArray[fromIndex];
//        }

        matrix.setPolyToPoly(source, 0, destination, 0, 4);
        return matrix;
    }


}