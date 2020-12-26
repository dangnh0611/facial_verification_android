package com.example.donelogin.ml;

import android.annotation.SuppressLint;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.Rect;
import android.media.Image;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;

import com.example.donelogin.OverlayView;
import com.example.donelogin.util.BitmapUtils;
import com.example.donelogin.util.Helper;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


public class FaceRegistrationPipeline {
    private ArrayList<float[]> faceEmbeddings = new ArrayList<float[]>();
    private boolean[] hasFaces = new boolean[] {false, false, false, false, false};

    public static final float[][][] angleXY = new float[][][]{
            { {-10, 10}, {-10, 10} },
            { {-10, 10}, {15, 30} },
            { {-10, 10}, {-30, -15} },
            { {15, 30}, {-10, 10} },
            { {-30, -15}, {-10, 10} }
    };

    private Executor executor= Executors.newSingleThreadExecutor();
    public static MatOfPoint2f FACE_BASE_LANDMARKS_5 = new MatOfPoint2f(
            new Point(38.2946, 51.6963),
            new Point(73.5318, 51.5014),
            new Point(56.0252, 71.7366),
            new Point(41.5493, 92.3655),
            new Point(70.7299, 92.2041)
    );
    public static int[] LANDMARKS_5 = {FaceLandmark.LEFT_EYE, FaceLandmark.RIGHT_EYE, FaceLandmark.NOSE_BASE, FaceLandmark.MOUTH_LEFT, FaceLandmark.MOUTH_RIGHT};
    public static int[] ALIGNED_FACE_SIZE = {112, 112};
    private FaceDetectorOptions faceDetectorOptions;
    private com.google.mlkit.vision.face.FaceDetector faceDetector;
    private OverlayView overlayView;
    private FaceRecognition faceRecognition;

    private static final String FACE_RECOGNITION_MODEL_FILE = "MobileFaceNet.tflite";

    public FaceRegistrationPipeline(AssetManager assetManager) throws IOException {

        // Start MLKit Face detector
        this.faceDetectorOptions = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .build();
        this.faceDetector = FaceDetection.getClient(faceDetectorOptions);

        // load recognition model file
        MappedByteBuffer frModelFile = Helper.loadModelFile(assetManager, FACE_RECOGNITION_MODEL_FILE);
        this.faceRecognition = new FaceRecognition(frModelFile);
    }

    private int checkFaceAngle(float x, float y){
        for(int i=0; i<5; i++){
            float[][] xyBase = angleXY[i];
            if( (x>xyBase[0][0]) && (x<xyBase[0][1]) && (y>xyBase[1][0]) && (y<xyBase[1][1]) ){
                return i;
            }
        }
        return -1;
    }


    private void updateOverlayView(OverlayView overlayView, List<Face> faces) {
        String msg= "";
        int numFace = faces.size();
        if(numFace != 1){
            msg="ERROR: " + numFace + " face detected!";
            overlayView.drawFaceBoundingBox(faces, msg, OverlayView.MSG_TYPE_ERROR);
        }
        else{
            float xRotation = faces.get(0).getHeadEulerAngleX();
            float yRotation = faces.get(0).getHeadEulerAngleY();
            msg= String.format("(%d/5) Y Angle: %d° | X Angle: %d°", faceEmbeddings.size(), (int)yRotation, (int)xRotation);
            overlayView.drawFaceBoundingBox(faces, msg, OverlayView.MSG_TYPE_INFO);
        }

    }

    public ArrayList<float[]> getFaceEmbeddings(){
        return this.faceEmbeddings;
    }

    public void recogniteAndUpdateState( Bitmap bitmap, Face face) {
        float xRotation = face.getHeadEulerAngleX();
        float yRotation = face.getHeadEulerAngleY();
        int idx = checkFaceAngle(xRotation, yRotation);
        if((idx==-1) || (hasFaces[idx])){
            return;
        }
        else{
            // Get affine similarity transformation matrix base on facial landmarks
            ArrayList<Point> faceLandmarks = new ArrayList<Point>();
            for (int landmarkType : LANDMARKS_5) {
                PointF landmarkPosition = face.getLandmark(landmarkType).getPosition();
                faceLandmarks.add(new Point(landmarkPosition.x, landmarkPosition.y));
            }
            MatOfPoint2f src = new MatOfPoint2f();
            src.fromList(faceLandmarks);
            Mat affineMatrix = Helper.similarityTransform(src, FACE_BASE_LANDMARKS_5);
            Log.d("AFFINE", affineMatrix.toString());

            Mat bitmapMat_CV_8UC4 = new Mat();
            // matrix form of rgb image
            Mat bitmapMat = new Mat(bitmapMat_CV_8UC4.rows(), bitmapMat_CV_8UC4.cols(), CvType.CV_8U);
            Utils.bitmapToMat(bitmap, bitmapMat_CV_8UC4);
            Imgproc.cvtColor(bitmapMat_CV_8UC4, bitmapMat, Imgproc.COLOR_RGBA2RGB);
            Log.d ("BITMAP", bitmapMat.toString());

            // get aligned face rgb image for recognition
            Mat faceMatAligned = new Mat(ALIGNED_FACE_SIZE[0], ALIGNED_FACE_SIZE[1], CvType.CV_8U);
            Imgproc.warpAffine(bitmapMat, faceMatAligned, affineMatrix, new Size(112, 112));
            Log.d("WARP", faceMatAligned.toString());
            faceMatAligned.convertTo(faceMatAligned, CvType.CV_32F);
            Core.subtract(faceMatAligned, new Scalar(127.5, 127.5, 127.5), faceMatAligned);
            Core.divide(faceMatAligned, new Scalar(128.0, 128.0, 128.0), faceMatAligned);

//            //  Test code for aligned face bitmap generator
//            Bitmap faceBitmapAligned = Bitmap.createBitmap(ALIGNED_FACE_SIZE[0], ALIGNED_FACE_SIZE[1], Bitmap.Config.ARGB_8888);
//            Utils.matToBitmap(faceMatAligned, faceBitmapAligned);
//            Log.d("ALIGN", faceBitmapAligned.toString());
//            // Test code
//            SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
//            File file = new File(Helper.getBatchDirectoryName(), mDateFormat.format(new Date())+ ".jpg");
//
//            try (FileOutputStream out = new FileOutputStream(file)) {
//                faceBitmapAligned.compress(Bitmap.CompressFormat.PNG, 100, out); // bmp is your Bitmap instance
//                // PNG is a lossless format, the compression factor (100) is ignored
//            } catch (IOException e) {
//                e.printStackTrace();
//            }

            // Perform recognition
            float[] embedding = this.faceRecognition.getEmbedding(faceMatAligned);
            faceEmbeddings.add(embedding);
            hasFaces[idx] = true;
        }
    }



//    public List<Face> detectFace(ImageProxy imageProxy) {
//        // input image convert
//        @SuppressLint("UnsafeExperimentalUsageError") Image mediaImage = imageProxy.getImage();
//        if (mediaImage != null) {
//            Log.d("IMAGE_PROXY_ROTATION", Integer.toString(imageProxy.getImageInfo().getRotationDegrees()));
//            InputImage inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
//
//            // Pass image to an ML Kit Vision API
//            Task<List<Face>> task =
//                    faceDetector.process(inputImage).addOnCompleteListener( executor,
//                            new OnCompleteListener<List<Face>>() {
//                                @Override
//                                public void onComplete(@NonNull Task<List<Face>> task) {
//                                    imageProxy.close();
//                                    Log.d("DONE", "DONE");
//                                }
//                            }
//                    )
//                            .addOnFailureListener( executor,
//                                    new OnFailureListener() {
//                                        @Override
//                                        public void onFailure(@NonNull Exception e) {
//                                            // Task failed with an exception
//                                            // ...
//                                        }
//                                    });
//            while (!task.isComplete()) {
//            }
//            return task.getResult();
//        }
//        return null;
//    }

//    }

    public ArrayList<float[]> detectFaceAsync(ImageProxy imageProxy, OverlayView overlayView) {
        if(this.faceEmbeddings.size() == 5){
//            imageProxy.close();
            return this.faceEmbeddings;
        }
        // input image convert
        @SuppressLint("UnsafeExperimentalUsageError") Image mediaImage = imageProxy.getImage();
//        Image.Plane[] planes = mediaImage.getPlanes();
//        Log.d("PLANE", mediaImage.getFormat() + "_" + planes.length + planes.toString());
//        if (mediaImage != null) {
//            Log.d("IMAGE_PROXY_ROTATION", Integer.toString(imageProxy.getImageInfo().getRotationDegrees()));
        InputImage inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

//        InputImage inputImage = InputImage.fromBitmap(inputBitmap, 0);

        // Pass image to an ML Kit Vision API
        Task<List<Face>> task =
                faceDetector.process(inputImage)
                        .addOnSuccessListener(
                                new OnSuccessListener<List<Face>>() {
                                    @Override
                                    public void onSuccess(List<Face> faces) {
                                        updateOverlayView(overlayView, faces);
                                    }
                                })
                        // executed on another executor to improve performance
                        .addOnCompleteListener( executor,
                                new OnCompleteListener<List<Face>>() {
                                    @Override
                                    public void onComplete(@NonNull Task<List<Face>> task) {
                                        List<Face> faces = task.getResult();
                                        if(faces.size()!=1){
                                            imageProxy.close();
                                            return;
                                        }
                                        else {
                                            @SuppressLint("UnsafeExperimentalUsageError") Bitmap inputBitmap = BitmapUtils.getBitmap(imageProxy);
                                            imageProxy.close();
                                            for (Face face : task.getResult()) {
                                                recogniteAndUpdateState(inputBitmap, face);
                                            }
                                        }
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
        return null;
    }


}



