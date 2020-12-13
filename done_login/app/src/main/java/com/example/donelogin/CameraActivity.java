package com.example.donelogin;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.extensions.HdrImageCaptureExtender;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import androidx.camera.core.ImageProxy;

import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.media.Image;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {

    private Executor executor = Executors.newSingleThreadExecutor();
    FaceDetectorOptions faceDetectorOptions;
    FaceDetector faceDetector;
    private OverlayView overlayView;
    private Matrix matrix=null;
    private int REQUEST_CODE_PERMISSIONS = 1001;
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE"};

    PreviewView mPreviewView;
    ImageView captureImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera);

        mPreviewView = findViewById(R.id.previewView);
        captureImage = findViewById(R.id.captureImg);

        overlayView = (OverlayView) findViewById( R.id.overlayView);
        // Necessary to keep the Overlay above the TextureView so that the boxes are visible.
        overlayView.setWillNotDraw( false );
        overlayView.setZOrderOnTop( true );

        if(allPermissionsGranted()){
            // Start MLKit Face detector
            faceDetectorOptions =
                    new FaceDetectorOptions.Builder()
                            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                            .build();
            faceDetector = FaceDetection.getClient(faceDetectorOptions);
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

                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
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
//                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetResolution(new Size(1200, 1600 ))
                .build();

//        mPreviewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1200, 1600))
//                .setTargetResolution(new Size(640, 480))
//                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(executor, new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy imageProxy) {
                // Main analyze code hear

                if (matrix!= null){}
                else{
                    matrix=getMappingMatrix(imageProxy, mPreviewView);
                    overlayView.setTransformMatrix(matrix);
                }
                @SuppressLint("UnsafeExperimentalUsageError") Image mediaImage = imageProxy.getImage();
                if (mediaImage != null) {
                    InputImage image =
                            InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                    Log.d("ROTATION", Integer.toString(imageProxy.getImageInfo().getRotationDegrees()));
                    // Pass image to an ML Kit Vision API
                    Task<List<Face>> result =
                            faceDetector.process(image)
                                    .addOnSuccessListener(
                                            new OnSuccessListener<List<Face>>() {
                                                @Override
                                                public void onSuccess(List<Face> faces) {
                                                    // Task completed successfully
                                                    // [START_EXCLUDE]
                                                    // [START get_face_info]
                                                    for (Face face : faces) {
                                                        Rect bounds = face.getBoundingBox();
                                                        float rotY = face.getHeadEulerAngleY();  // Head is rotated to the right rotY degrees
                                                        float rotZ = face.getHeadEulerAngleZ();  // Head is tilted sideways rotZ degrees

                                                        // If landmark detection was enabled (mouth, ears, eyes, cheeks, and
                                                        // nose available):
                                                        FaceLandmark leftEar = face.getLandmark(FaceLandmark.NOSE_BASE);
                                                        if (leftEar != null) {
                                                            PointF leftEarPos = leftEar.getPosition();
                                                            Log.d("ABCDEF", leftEarPos.toString());
                                                        }

                                                    }
                                                    // [END get_face_info]
                                                    // [END_EXCLUDE]
                                                    // Clear the BoundingBoxOverlay and set the new results ( boxes ) to be displayed.
                                                    if (faces != null){
                                                        overlayView.setFaces(faces);
                                                        overlayView.invalidate();
                                                    }
                                                }
                                            })
                                    .addOnCompleteListener(
                                            new OnCompleteListener<List<Face>>() {
                                                @Override
                                                public void onComplete(@NonNull Task<List<Face>> task) {
                                                    imageProxy.close();
                                                }
                                            }
                                    )
                                    .addOnFailureListener(
                                            new OnFailureListener() {
                                                @Override
                                                public void onFailure(@NonNull Exception e) {
                                                    // Task failed with an exception
                                                    // ...
                                                }
                                            });
                }
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
                            Toast.makeText(CameraActivity.this, "Image Saved successfully", Toast.LENGTH_SHORT).show();
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

    private Matrix getMappingMatrix(ImageProxy imageProxy, PreviewView previewView) {
        // Our boxes will be predicted on a 640 * 480 image. So, we need to scale the boxes to the device screen's width and
        // height

        Rect cropRect = imageProxy.getCropRect();

//        float xfactor = (float)previewView.getWidth() / cropRect.right;
//        float yfactor = (float)previewView.getHeight() / cropRect.bottom;
//        // Create a Matrix for scaling
//        Matrix matrix= new Matrix();
//        Log.d("FACTOR", Float.toString(xfactor) + Float.toString(yfactor));
//        matrix.preScale(xfactor, yfactor);
//        Log.d("SOURCE", cropRect.flattenToString());
//        Log.d("SOURCE", Float.toString(cropRect.top) + Float.toString(cropRect.left)+ Float.toString(cropRect.right) + "___" + Float.toString(cropRect.bottom));
//        Log.d("DESTINATION", Float.toString(previewView.getWidth()) + "___" + Float.toString(previewView.getHeight()));
//        return matrix;


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
        Log.d("SOURCE", Float.toString(cropRect.right) + "___" + Float.toString(cropRect.bottom));
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