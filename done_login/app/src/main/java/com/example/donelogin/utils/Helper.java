package com.example.donelogin.utils;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;

import org.opencv.core.Mat;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.MatOfPoint2f;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;

public class Helper {

    public static Mat similarityTransform(MatOfPoint2f src, MatOfPoint2f dst){
        Mat matrix = Calib3d.estimateAffine2D(src, dst);
        return matrix;
    }


    public static MappedByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public static float[][][] convert8UC3ToFloatArray(Mat mat){
        int w = mat.cols();
        int h = mat.rows();
        float[][][] arr = new float[h][w][3];

        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                double[] rgb = mat.get(i, j);
                arr[i][j]= new float[]{(float)rgb[0], (float)rgb[1], (float)rgb[2] };
            }
        }
        return arr;
    }

    public static float min(float[] arr){
        float min = arr[0];
        for (int i=1; i< arr.length; i++){
            if(arr[i] < min){
                min = arr[i];
            }
        }
        return min;
    }

    public static float calculateEuclidDistance(float[] v1, float[] v2){
        float dist =0;
        for(int i=0; i< v1.length; i++){
            dist += (v1[i] - v2[i]) * (v1[i] - v2[i]);
        }
        return dist;
    }

}
