package com.hpp.daftree.utils;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

public class TouchImageView extends androidx.appcompat.widget.AppCompatImageView {
    private Matrix matrix = new Matrix();
    private PointF lastTouch = new PointF();
    private float minScale = 1.0f;
    private float maxScale = 5.0f;
    private float[] m;
    private int viewWidth, viewHeight;
    private int drawableWidth, drawableHeight;
    private ScaleGestureDetector scaleDetector;
    private int mode = 0;
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;

    public TouchImageView(Context context) {
        super(context);
        init(context);
    }

    public TouchImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        super.setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        m = new float[9];
        setImageMatrix(matrix);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        viewWidth = MeasureSpec.getSize(widthMeasureSpec);
        viewHeight = MeasureSpec.getSize(heightMeasureSpec);
        if (getDrawable() != null) {
            drawableWidth = getDrawable().getIntrinsicWidth();
            drawableHeight = getDrawable().getIntrinsicHeight();
            fitImageToView();
        }
    }

    private void fitImageToView() {
        float scale;
        float scaleX = (float) viewWidth / drawableWidth;
        float scaleY = (float) viewHeight / drawableHeight;
        scale = Math.min(scaleX, scaleY);

        matrix.setScale(scale, scale);

        // مركز الصورة
        float redundantXSpace = viewWidth - (scale * drawableWidth);
        float redundantYSpace = viewHeight - (scale * drawableHeight);
        matrix.postTranslate(redundantXSpace / 2, redundantYSpace / 2);

        minScale = scale;
        setImageMatrix(matrix);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);

        matrix.getValues(m);
        float x = m[Matrix.MTRANS_X];
        float y = m[Matrix.MTRANS_Y];
        PointF curr = new PointF(event.getX(), event.getY());

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                lastTouch.set(event.getX(), event.getY());
                mode = DRAG;
                break;

            case MotionEvent.ACTION_MOVE:
                if (mode == DRAG) {
                    float deltaX = curr.x - lastTouch.x;
                    float deltaY = curr.y - lastTouch.y;

                    float scale = m[Matrix.MSCALE_X];
                    float limitX = (viewWidth * (scale - minScale)) / 2;
                    float limitY = (viewHeight * (scale - minScale)) / 2;

                    if (scale > minScale) {
                        if (x + deltaX > limitX) deltaX = limitX - x;
                        else if (x + deltaX < -limitX) deltaX = -limitX - x;

                        if (y + deltaY > limitY) deltaY = limitY - y;
                        else if (y + deltaY < -limitY) deltaY = -limitY - y;

                        matrix.postTranslate(deltaX, deltaY);
                        setImageMatrix(matrix);
                        lastTouch.set(curr.x, curr.y);
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                mode = NONE;
                break;
        }

        return true;
    }

    public void setZoom(float scale, float focusX, float focusY) {
        float currentScale = m[Matrix.MSCALE_X];
        if ((scale < minScale && currentScale <= minScale) ||
                (scale > maxScale && currentScale >= maxScale)) {
            return;
        }

        scale = Math.max(minScale, Math.min(scale, maxScale));
        matrix.postScale(scale / currentScale, scale / currentScale, focusX, focusY);
        setImageMatrix(matrix);
    }

    public void resetZoom() {
        fitImageToView();
    }

    public float getCurrentZoom() {
        matrix.getValues(m);
        return m[Matrix.MSCALE_X];
    }

    public void setMaxZoom(float zoom) {
        maxScale = zoom;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            float currentScale = m[Matrix.MSCALE_X];

            if ((currentScale < maxScale && scaleFactor > 1.0f) ||
                    (currentScale > minScale && scaleFactor < 1.0f)) {

                float newScale = currentScale * scaleFactor;
                if (newScale > maxScale) newScale = maxScale;
                if (newScale < minScale) newScale = minScale;

                matrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
                setImageMatrix(matrix);
            }
            return true;
        }
    }
}