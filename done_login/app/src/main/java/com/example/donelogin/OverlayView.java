package com.example.donelogin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

import com.google.mlkit.vision.face.Face;

import java.util.ArrayList;
import java.util.List;

// Defines an overlay on which the boxes and text will be drawn.
public class OverlayView extends SurfaceView implements SurfaceHolder.Callback{
    private Paint boxPaint, textPaint;
    public List<Face> faces;
    Matrix matrix;


    public OverlayView(Context context, AttributeSet attributeSet){
        super(context, attributeSet);
        boxPaint = new Paint();
        boxPaint.setColor( Color.parseColor( "#4D90caf9" ));
        boxPaint.setStyle(Paint.Style.FILL);

        textPaint=new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setStrokeWidth(2.0f);
        textPaint.setTextSize(32f);
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
                RectF processedBbox = processBBox( face.getBoundingBox() );
                Log.d("ONDRAW_SECOND", processedBbox.toString());
                // Draw boxes and text
                canvas.drawRoundRect( processedBbox , 16f , 16f , boxPaint );
                canvas.drawRoundRect( processBBox(new Rect(0, 0, 100, 100)), 16, 16, boxPaint);
                Log.d("TEST", processBBox(new Rect(0, 0, 100, 100)).toString());
//                canvas.drawRoundRect( new RectF(0,100,200,300) , 16f , 16f , boxPaint );
                canvas.drawText(
                        Float.toString(face.getHeadEulerAngleY()),
                        processedBbox.centerX() ,
                        processedBbox.centerY() ,
                        textPaint
                );
            }
        }
    }

    private RectF processBBox(Rect bbox ) {
        RectF rectf = new RectF( bbox );
        matrix.mapRect( rectf);
        return rectf;
    }

    public void setFaces(List<Face> faces){
        this.faces= faces;
    }

    public void setTransformMatrix(Matrix matrix){
        this.matrix=matrix;
    }

}