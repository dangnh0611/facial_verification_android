package com.example.donelogin.activity;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import androidx.camera.core.ImageProxy;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.donelogin.OverlayView;
import com.example.donelogin.R;
import com.example.donelogin.ml.FaceDetector;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FaceRegistrationCameraActivity extends AppCompatActivity {

    private Executor executor = Executors.newSingleThreadExecutor();
//    private Executor executor= Executors.newFixedThreadPool(8);

    private OverlayView overlayView;
    private Matrix transformationMatrix=null;
    private FaceDetector faceDetector;
    private int REQUEST_CODE_PERMISSIONS = 1001;
    private final String[] REQUIRED_PERMISSIONS = new String[]{ "android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE" };

    PreviewView mPreviewView;
    ImageView captureImage;
    double _temp_time = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera);

        mPreviewView = findViewById(R.id.previewView);

        // Overlay View for bounding box drawing, ..
        overlayView = (OverlayView) findViewById( R.id.overlayView);
        captureImage = findViewById(R.id.captureImg);

        // Necessary to keep the Overlay above the TextureView so that the boxes are visible.
        overlayView.setWillNotDraw( false );
        overlayView.setZOrderOnTop( true );

        try {
            faceDetector=new FaceDetector(getAssets());
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
                faceDetector.detectFaceAsync(imageProxy, overlayView);
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

        final ImageCapture imageCapture = builder
                .setTargetRotation(this.getWindowManager().getDefaultDisplay().getRotation())
                .build();

        preview.setSurfaceProvider(mPreviewView.getSurfaceProvider());

        Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, preview, imageAnalysis, imageCapture);

        captureImage.setOnClickListener(v -> {

            SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
            File file = new File(getBatchDirectoryName(), mDateFormat.format(new Date())+ ".jpg");

            ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(file).build();
            imageCapture.takePicture(outputFileOptions, executor, new ImageCapture.OnImageSavedCallback () {
                @Override
                public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(FaceRegistrationCameraActivity.this, "Image Saved successfully", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                @Override
                public void onError(@NonNull ImageCaptureException error) {
                    error.printStackTrace();
                }
            });
        });
    }

    public String getBatchDirectoryName() {

        String app_folder_path = "";
        app_folder_path = Environment.getExternalStorageDirectory().toString() + "/images";
        File dir = new File(app_folder_path);
        if (!dir.exists() && !dir.mkdirs()) {

        }

        return app_folder_path;
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