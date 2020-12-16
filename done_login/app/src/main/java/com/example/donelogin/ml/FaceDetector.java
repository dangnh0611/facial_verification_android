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
import com.example.donelogin.utils.BitmapUtils;
import com.example.donelogin.utils.Helper;
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
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;


public class FaceDetector {
    public static MatOfPoint2f FACE_BASE_LANDMARKS_5 = new MatOfPoint2f(
            new Point(38.2946, 51.6963),
            new Point(73.5318, 51.5014),
            new Point(56.0252, 71.7366),
            new Point(41.5493, 92.3655),
            new Point(70.7299, 92.2041)
    );
    public static int[] LANDMARKS_5 = {FaceLandmark.LEFT_EYE, FaceLandmark.RIGHT_EYE, FaceLandmark.NOSE_BASE, FaceLandmark.MOUTH_LEFT, FaceLandmark.MOUTH_RIGHT};

    public static int[] ALIGNED_FACE_SIZE = {112, 112};
    public static int[] CROP_FACE_SIZE = {256, 256};
    FaceDetectorOptions faceDetectorOptions;
    com.google.mlkit.vision.face.FaceDetector faceDetector;
    OverlayView overlayView;

    private static final String FACE_ANTI_SPOOFING_MODEL_FILE = "FaceAntiSpoofing.tflite";

    public FaceDetector(AssetManager assetManager) throws IOException {

        // Start MLKit Face detector
        this.faceDetectorOptions = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .build();
        this.faceDetector = FaceDetection.getClient(faceDetectorOptions);

        // load antispoofing model file

        Helper.loadModelFile(assetManager, FACE_ANTI_SPOOFING_MODEL_FILE);
    }

    public static void drawBoundingBox(OverlayView overlayView, List<Face> faces) {
        overlayView.drawFaceBoundingBox(faces);
    }


    public static void alignFace( Bitmap bitmap, Face face) {
        // crop face


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
        Mat bitmapMat = new Mat();
        Utils.bitmapToMat(bitmap, bitmapMat_CV_8UC4);
        Imgproc.cvtColor(bitmapMat_CV_8UC4, bitmapMat, Imgproc.COLOR_RGBA2RGB);
        Log.d ("BITMAP", bitmapMat.toString());

        // get aligned face rgb image for recognition
        Mat faceMatAligned = new Mat(ALIGNED_FACE_SIZE[0], ALIGNED_FACE_SIZE[1], CvType.CV_32F);
        Imgproc.warpAffine(bitmapMat, faceMatAligned, affineMatrix, new Size(112, 112));
        Log.d("WARP", faceMatAligned.toString());

        // get cropped face rgb image for antispoofing
        Rect _bbox = face.getBoundingBox();
        org.opencv.core.Rect bbox = new org.opencv.core.Rect(Math.max((int)_bbox.left, 0), Math.max((int)_bbox.top, 0),
                Math.min((int)(_bbox.right- _bbox.left), bitmapMat.cols()- _bbox.left), Math.min((int)(_bbox.bottom - _bbox.top), bitmapMat.rows()-_bbox.top));
        Mat _faceCroppedMat = new Mat(bitmapMat, bbox);
        Mat faceCroppedMat = new Mat();
        Imgproc.resize(_faceCroppedMat, faceCroppedMat, new Size(CROP_FACE_SIZE[0], CROP_FACE_SIZE[1]));


        //  Test code for aligned face bitmap generator
//        Bitmap faceBitmapAligned = Bitmap.createBitmap(ALIGNED_FACE_SIZE[0], ALIGNED_FACE_SIZE[1], Bitmap.Config.ARGB_8888);
//        Utils.matToBitmap(faceMatAligned, faceBitmapAligned);
//        Log.d("ALIGN", faceBitmapAligned.toString());

//        Bitmap faceBitmapAligned = Bitmap.createBitmap(CROP_FACE_SIZE[0], CROP_FACE_SIZE[1], Bitmap.Config.ARGB_8888);
//        Utils.matToBitmap(faceCroppedMat, faceBitmapAligned);
//        Log.d("ALIGN", faceBitmapAligned.toString());

      // Test code
//        SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
//        File file = new File(getBatchDirectoryName(), mDateFormat.format(new Date())+ ".jpg");
//
//        try (FileOutputStream out = new FileOutputStream(file)) {
//            faceBitmapAligned.compress(Bitmap.CompressFormat.PNG, 100, out); // bmp is your Bitmap instance
//            // PNG is a lossless format, the compression factor (100) is ignored
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        
    }

//    public static String getBatchDirectoryName() {
//
//        String app_folder_path = "";
//        app_folder_path = Environment.getExternalStorageDirectory().toString() + "/images";
//        File dir = new File(app_folder_path);
//        if (!dir.exists() && !dir.mkdirs()) {
//
//        }
//
//        return app_folder_path;
//    }

    public List<Face> detectFace(ImageProxy imageProxy) {
        // input image convert
        @SuppressLint("UnsafeExperimentalUsageError") Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            Log.d("IMAGE_PROXY_ROTATION", Integer.toString(imageProxy.getImageInfo().getRotationDegrees()));
            InputImage inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            // Pass image to an ML Kit Vision API
            Task<List<Face>> task =
                    faceDetector.process(inputImage).addOnCompleteListener(
                            new OnCompleteListener<List<Face>>() {
                                @Override
                                public void onComplete(@NonNull Task<List<Face>> task) {
                                    imageProxy.close();
                                    Log.d("DONE", "DONE");
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
            while (!task.isComplete()) {
            }
            return task.getResult();
        }
        return null;
    }

//    }

    public void detectFaceAsync(ImageProxy imageProxy, OverlayView overlayView) {
        // input image convert
        @SuppressLint("UnsafeExperimentalUsageError") Image mediaImage = imageProxy.getImage();
//        Image.Plane[] planes = mediaImage.getPlanes();
//        Log.d("PLANE", mediaImage.getFormat() + "_" + planes.length + planes.toString());
//        if (mediaImage != null) {
//            Log.d("IMAGE_PROXY_ROTATION", Integer.toString(imageProxy.getImageInfo().getRotationDegrees()));
            InputImage inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
        @SuppressLint("UnsafeExperimentalUsageError") Bitmap inputBitmap = BitmapUtils.getBitmap(imageProxy);
//        InputImage inputImage = InputImage.fromBitmap(inputBitmap, 0);

        // Pass image to an ML Kit Vision API
        Task<List<Face>> task =
                faceDetector.process(inputImage)
                        .addOnSuccessListener(
                                new OnSuccessListener<List<Face>>() {
                                    @Override
                                    public void onSuccess(List<Face> faces) {
                                        imageProxy.close();
                                        drawBoundingBox(overlayView, faces);
                                        Log.d("DONE", "drawed bounding box!");

                                        for (Face face: faces){
                                            alignFace(inputBitmap, face);
                                        }


                                    }
                                })
                        .addOnCompleteListener(
                                new OnCompleteListener<List<Face>>() {
                                    @Override
                                    public void onComplete(@NonNull Task<List<Face>> task) {
//                                        imageProxy.close();
//                                        List<Face> faces = task.getResult();
//                                        drawBoundingBox(overlayView, faces);
//                                        Log.d("DONE", "DONE");

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



