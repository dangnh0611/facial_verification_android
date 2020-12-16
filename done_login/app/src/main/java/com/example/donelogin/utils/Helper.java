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

}
