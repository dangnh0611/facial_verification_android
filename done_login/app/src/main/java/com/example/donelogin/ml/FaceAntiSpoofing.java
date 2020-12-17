package com.example.donelogin.ml;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;
import android.widget.TableRow;
import android.widget.Toast;

import com.example.donelogin.utils.Helper;

import org.opencv.core.Mat;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.support.model.Model;

import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.MappedByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static com.google.android.material.internal.ContextUtils.getActivity;

public class FaceAntiSpoofing {
    public static final float THRESHOLD = 0.2f;

    public static final int ROUTE_INDEX = 5;

//    public static final int LAPLACE_THRESHOLD = 50;
//    public static final int LAPLACIAN_THRESHOLD = 1000;
    private Interpreter interpreter;

    public FaceAntiSpoofing(MappedByteBuffer modelFile) throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        CompatibilityList compatList = new CompatibilityList();

        if(compatList.isDelegateSupportedOnThisDevice()){
            // if the device has a supported GPU, add the GPU delegate
//            GpuDelegate.Options delegateOptions = compatList.getBestOptionsForThisDevice();
//            GpuDelegate gpuDelegate = new GpuDelegate(delegateOptions);
//            options.addDelegate(gpuDelegate);
            Log.d ("GPU@CPU", "GPU is available!");
        } else {
            // if the GPU is not supported, run on 4 threads
            options.setNumThreads(4);
            Log.d("GPU@CPU", "GPU not available, use CPU instead!");
        }

        options.setNumThreads(4);
        interpreter = new Interpreter(modelFile, options);
    }


    public float score(Mat mat) {

        float[][][] img = Helper.convert8UC3ToFloatArray(mat);
        float[][][][] input = new float[1][][][];
        input[0] = img;
        float[][] clss_pred = new float[1][8];
        float[][] leaf_node_mask = new float[1][8];
        Map<Integer, Object> outputs = new HashMap<>();
        outputs.put(interpreter.getOutputIndex("Identity"), clss_pred);
        outputs.put(interpreter.getOutputIndex("Identity_1"), leaf_node_mask);
        long start= System.currentTimeMillis();
        interpreter.runForMultipleInputsOutputs(new Object[]{input}, outputs);
        long end= System.currentTimeMillis();
        Log.d("FAS_TIME", Double.toString(end-start) + " ms");

        Log.d("SCORE CLSS", "[0=" + clss_pred[0][0] + ",1= " + clss_pred[0][1] + ",2= "
                + clss_pred[0][2] + ",3= " + clss_pred[0][3] + ",4= " + clss_pred[0][4] + ",5= "
                + clss_pred[0][5] + ",6= " + clss_pred[0][6] + ",7= " + clss_pred[0][7] + "]");
        Log.d("SCORE MASK", "[" + leaf_node_mask[0][0] + ", " + leaf_node_mask[0][1] + ", "
                + leaf_node_mask[0][2] + ", " + leaf_node_mask[0][3] + ", " + leaf_node_mask[0][4] + ", "
                + leaf_node_mask[0][5] + ", " + leaf_node_mask[0][6] + ", " + leaf_node_mask[0][7] + "]");

            return calculateScoreByMin(clss_pred);
    }

    public boolean isRealFace(Mat mat){
        float score = score(mat);
        boolean isReal = score < THRESHOLD;
        if(isReal){
            Log.d("SCORE", "REAL " + Float.toString(score));
        }
        else{
            Log.d("SCORE", "FAKE " + Float.toString(score));
        }
        return isReal;
    }


    public float calculateScoreByMin(float[][] clss_pred){
        return Helper.min(clss_pred[0]);
    }

    private float calculateScoreByMax(float[][] clss_pred, float[][] leaf_node_mask) {
        float score = 0;
        for (int i = 0; i < 8; i++) {
            score += Math.abs(clss_pred[0][i]) * leaf_node_mask[0][i];
        }
        return score;
    }

    private float calculateScoreByIndex(float[][] clss_pred)
    {
        return clss_pred[0][ROUTE_INDEX];
    }


//    public int laplacian(Bitmap bitmap) {
//        // 将人脸resize为256X256大小的，因为下面需要feed数据的placeholder的形状是(1, 256, 256, 3)
//        Bitmap bitmapScale = Bitmap.createScaledBitmap(bitmap, INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE, true);
//
//        int[][] laplace = {{0, 1, 0}, {1, -4, 1}, {0, 1, 0}};
//        int size = laplace.length;
//        int[][] img = MyUtil.convertGreyImg(bitmapScale);
//        int height = img.length;
//        int width = img[0].length;
//
//        int score = 0;
//        for (int x = 0; x < height - size + 1; x++){
//            for (int y = 0; y < width - size + 1; y++){
//                int result = 0;
//                // 对size*size区域进行卷积操作
//                for (int i = 0; i < size; i++){
//                    for (int j = 0; j < size; j++){
//                        result += (img[x + i][y + j] & 0xFF) * laplace[i][j];
//                    }
//                }
//                if (result > LAPLACE_THRESHOLD) {
//                    score++;
//                }
//            }
//        }
//        return score;
//    }



}