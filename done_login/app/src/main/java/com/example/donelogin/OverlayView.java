package com.example.donelogin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.ArrayList;
import java.util.List;

// Defines an overlay on which the boxes and text will be drawn.
public class OverlayView extends SurfaceView implements SurfaceHolder.Callback {
    private Paint boxPaint, dotPaint, textInfoPaint, textWarningPaint, textErrorPaint;
    private List<Face> faces;
    private String msg;
    private int msgType;
    public static final int MSG_TYPE_ERROR = 0;
    public static final int MSG_TYPE_WARNING = 1;
    public static final int MSG_TYPE_INFO = 2;
    Matrix matrix;


    public OverlayView(Context context, AttributeSet attributeSet){
        super(context, attributeSet);
        boxPaint = new Paint();
        boxPaint.setColor( Color.parseColor( "#4D90caf9" ));
        boxPaint.setStyle(Paint.Style.FILL);

        dotPaint= new Paint();
        dotPaint.setColor(Color.GREEN);

        textInfoPaint=new Paint();
        textInfoPaint.setColor(Color.WHITE);
        textInfoPaint.setStrokeWidth(2.0f);
        textInfoPaint.setTextSize(45f);

        textWarningPaint=new Paint();
        textWarningPaint.setColor(Color.GREEN);
        textWarningPaint.setStrokeWidth(2.0f);
        textWarningPaint.setTextSize(45f);

        textErrorPaint=new Paint();
        textErrorPaint.setColor(Color.RED);
        textErrorPaint.setStrokeWidth(2.0f);
        textErrorPaint.setTextSize(45f);

    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {

    }

    @Override
    protected void onDraw(Canvas canvas) {
        if ( faces != null ) {
            for ( Face face : faces) {
                Log.d("ONDRAW_FIRST", face.getBoundingBox().toString());
                RectF processedBbox = transformRect( face.getBoundingBox() );
                Log.d("ONDRAW_SECOND", processedBbox.toString());
                // Draw boxes and text
                canvas.drawRoundRect( processedBbox , 16f , 16f , boxPaint );
                Paint textPaint;
                switch (this.msgType){
                    case MSG_TYPE_WARNING:
                        textPaint= textWarningPaint;
                        break;
                    case MSG_TYPE_INFO:
                        textPaint= textInfoPaint;
                        break;
                    case MSG_TYPE_ERROR:
                        textPaint= textErrorPaint;
                        break;
                    default:
                        textPaint = textErrorPaint;
                }
                canvas.drawText(
                        msg,
                        0,150,
                        textPaint
                );
                List<FaceLandmark> landmarks = face.getAllLandmarks();
                for (FaceLandmark landmark: landmarks){
                    float[] pointf= transformPoint(landmark.getPosition());
                    canvas.drawCircle(pointf[0], pointf[1], 10, dotPaint);
                }
            }
        }
    }

    private RectF transformRect(Rect bbox ) {
        RectF rectf = new RectF( bbox );
        matrix.mapRect( rectf);
        return rectf;
    }

    private float[] transformPoint(PointF point){
        float[] pointf = { point.x, point.y };
        matrix.mapPoints(pointf);
        return pointf;
    }

    private void setFaces(List<Face> faces){
        this.faces= faces;
    }

    private void setMsg(String msg){
        this.msg= msg;
    }

    private void setMsgType(int msgType){
        this.msgType= msgType;
    }

    public void setTransformationMatrix(Matrix matrix){
        this.matrix=matrix;
    }

    public void drawFaceBoundingBox(List<Face> faces, String msg, int msgType){
        this.setFaces(faces);
        this.setMsg(msg);
        this.setMsgType(msgType);
        this.invalidate();
    }

}