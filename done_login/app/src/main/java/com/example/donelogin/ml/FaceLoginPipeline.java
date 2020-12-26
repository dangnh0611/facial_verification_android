package com.example.donelogin.ml;

import android.annotation.SuppressLint;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.Rect;
import android.media.Image;
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

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


public class FaceLoginPipeline {
    private boolean trueReal = false;
    private int nFail = 0;
    private float[][] savedFaceEmbeddings;

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
    public static int[] CROP_FACE_SIZE = {256, 256};
    private FaceDetectorOptions faceDetectorOptions;
    private com.google.mlkit.vision.face.FaceDetector faceDetector;
    private OverlayView overlayView;
    private FaceAntiSpoofing faceAntiSpoofing;
    private FaceRecognition faceRecognition;

    private static final String FACE_ANTI_SPOOFING_MODEL_FILE = "FaceAntiSpoofing.tflite";
    private static final String FACE_RECOGNITION_MODEL_FILE = "MobileFaceNet.tflite";

    public FaceLoginPipeline(AssetManager assetManager, float[][] savedFaceEmbeddings) throws IOException {
        this.savedFaceEmbeddings = savedFaceEmbeddings;
        // Start MLKit Face detector
        this.faceDetectorOptions = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .build();
        this.faceDetector = FaceDetection.getClient(faceDetectorOptions);

        // load antispoofing model file
        MappedByteBuffer fasModelFile= Helper.loadModelFile(assetManager, FACE_ANTI_SPOOFING_MODEL_FILE);
        this.faceAntiSpoofing = new FaceAntiSpoofing(fasModelFile);
        // load recognition model file
        MappedByteBuffer frModelFile = Helper.loadModelFile(assetManager, FACE_RECOGNITION_MODEL_FILE);
        this.faceRecognition = new FaceRecognition(frModelFile);
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
            msg= String.format("Y Angle: %d° | X Angle: %d°", (int)yRotation, (int)xRotation);
            overlayView.drawFaceBoundingBox(faces, msg, OverlayView.MSG_TYPE_INFO);
        }

    }


    public void recogniteAndUpdateState( Bitmap bitmap, Face face) {
        if(this.trueReal){
            Log.d("LOGIN", "end here");
            return;
        }
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
        double[] temp = faceMatAligned.get(100, 100);
        Log.d("TEST", temp[0]  + "_" + temp[1] + "_" + temp[2] + "__" + temp.length);

        // get cropped face rgb image for antispoofing
        Rect _bbox = face.getBoundingBox();
        org.opencv.core.Rect bbox = new org.opencv.core.Rect(
                Math.max((int)_bbox.left, 0),
                Math.max((int)_bbox.top, 0),
                Math.min((int)_bbox.right- (int)_bbox.left, bitmapMat.cols()- 1- (int)_bbox.left),
                Math.min((int)_bbox.bottom - (int)_bbox.top, bitmapMat.rows() -1- (int)_bbox.top)
        );
        Log.d("ROWBUG", bbox.toString() + "__" + bitmapMat.rows() + "__" + (int)_bbox.top);

        Mat _faceCroppedMat = new Mat(bitmapMat, bbox);
        Mat faceCroppedMat = new Mat();
        Imgproc.resize(_faceCroppedMat, faceCroppedMat, new Size(CROP_FACE_SIZE[0], CROP_FACE_SIZE[1]));
        Log.d("WARP1", faceCroppedMat.toString());
        faceCroppedMat.convertTo(faceCroppedMat, CvType.CV_32F);
        Core.divide(faceCroppedMat, new Scalar(255.0, 255.0, 255.0), faceCroppedMat);

        //  Test code for aligned face bitmap generator
//        Bitmap faceBitmapAligned = Bitmap.createBitmap(ALIGNED_FACE_SIZE[0], ALIGNED_FACE_SIZE[1], Bitmap.Config.ARGB_8888);
//        Utils.matToBitmap(faceMatAligned, faceBitmapAligned);
//        Log.d("ALIGN", faceBitmapAligned.toString());

//        Bitmap faceBitmapAligned = Bitmap.createBitmap(CROP_FACE_SIZE[0], CROP_FACE_SIZE[1], Bitmap.Config.ARGB_8888);
//        Utils.matToBitmap(faceCroppedMat, faceBitmapAligned);
//        Log.d("ALIGN", faceBitmapAligned.toString());

      // Test code
//        SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
//        File file = new File(Helper.getBatchDirectoryName(), mDateFormat.format(new Date())+ ".jpg");
//
//        try (FileOutputStream out = new FileOutputStream(file)) {
//            faceBitmapAligned.compress(Bitmap.CompressFormat.PNG, 100, out); // bmp is your Bitmap instance
//            // PNG is a lossless format, the compression factor (100) is ignored
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        // Perform recognition
        float[] embedding = this.faceRecognition.getEmbedding(faceMatAligned);
        boolean isSame = false;
        for(float[] savedEmbedding : savedFaceEmbeddings ){
            boolean _isSame = this.faceRecognition.isSame(embedding, savedEmbedding);
            if(_isSame==true){
                isSame = true;
                break;
            }
        }
        // if it is a same face, check liveness
        if(isSame){
            // Perform antispoofing
            boolean isReal = faceAntiSpoofing.isRealFace(faceCroppedMat);
            if(isReal){
                this.trueReal = true;
                Log.d("LOGIN", "SAME, REAL");
            }
            else{
                Log.d("LOGIN", "SAME, FAKE");
            }
        }
        else{
            Log.d("LOGIN", "DIFFERENT");
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

    /*
    Return:
        0: OK
        1: WAITING, continue to verify
        2: FAIL, stop verify
     */
    public int verifyFaceAsync(ImageProxy imageProxy, OverlayView overlayView) {
        if(this.trueReal==true){
            Log.d("LOGIN", "return");
//            imageProxy.close();
            return 0;
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
                        .addOnCompleteListener( executor,
                                new OnCompleteListener<List<Face>>() {
                                    @Override
                                    public void onComplete(@NonNull Task<List<Face>> task) {
                                        List<Face> faces = task.getResult();
                                        if(faces.size()>0){
                                            @SuppressLint("UnsafeExperimentalUsageError") Bitmap inputBitmap = BitmapUtils.getBitmap(imageProxy);
                                            imageProxy.close();
                                            for (Face face: faces) {
                                                recogniteAndUpdateState(inputBitmap, face);
                                            }
                                        }
                                        else{
                                            imageProxy.close();
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
        return 1;
    }


}



