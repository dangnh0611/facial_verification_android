package com.example.donelogin.ml;


import android.util.Log;

import com.example.donelogin.util.Helper;

import org.opencv.core.Mat;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.IOException;
import java.nio.MappedByteBuffer;

/**
 * 人脸比对
 */
public class FaceRecognition {
    public static int EMBEDDING_SIZE = 128;
    public static float THRESHOLD = 1.0f;

    private Interpreter interpreter;

    public FaceRecognition(MappedByteBuffer modelFile) throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        CompatibilityList compatList = new CompatibilityList();

        if(compatList.isDelegateSupportedOnThisDevice()){
            // if the device has a supported GPU, add the GPU delegate
            GpuDelegate.Options delegateOptions = compatList.getBestOptionsForThisDevice();
            GpuDelegate gpuDelegate = new GpuDelegate(delegateOptions);
            options.addDelegate(gpuDelegate);
            Log.d ("GPU@CPU", "GPU is available for MobileFaceNet!");
        } else {
            // if the GPU is not supported, run on 4 threads
            options.setNumThreads(4);
            Log.d("GPU@CPU", "GPU not available for MobileFaceNet, use CPU instead!");
        }

//        options.setNumThreads(4);
        interpreter = new Interpreter(modelFile, options);
    }

    public float[] getEmbedding(Mat mat) {
        float[][][] img = Helper.convert8UC3ToFloatArray(mat);
        float[][][][] input = new float[1][][][];
        input[0] = img;
        float[][] embedding = new float[1][EMBEDDING_SIZE];
        interpreter.run(input, embedding);
        double t = interpreter.getLastNativeInferenceDurationNanoseconds();
        Log.d("RECOG", Double.toString(t/1000000));
        return embedding[0];
    }


    public  boolean isSame(float[] embedding1, float[] embedding2) {
        float dist = Helper.calculateEuclidDistance(embedding1, embedding2);
        Log.d("RECOG", "IS SAME? " + Boolean.toString(dist< THRESHOLD) + "  " + Float.toString(dist));
        return dist < THRESHOLD;
    }
}
