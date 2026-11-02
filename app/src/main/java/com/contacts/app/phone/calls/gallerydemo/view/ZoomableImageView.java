package com.contacts.app.phone.calls.gallerydemo.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.appcompat.widget.AppCompatImageView;

// An ImageView you can pinch to zoom, drag when zoomed, and double-tap to zoom in/out.
// The picture is drawn through a Matrix (a table of numbers for scale and position).
public class ZoomableImageView extends AppCompatImageView {

    private static final float MAX_ZOOM = 4f;        // relative to the "fit to screen" size
    private static final float DOUBLE_TAP_ZOOM = 2.5f;

    private final Matrix zoomMatrix = new Matrix();
    private final float[] matrixValues = new float[9];
    private final RectF tempRect = new RectF();

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private float fitScale = 1f;

    public ZoomableImageView(Context context) {
        super(context);
        init(context);
    }

    public ZoomableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomableImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setScaleType(ScaleType.MATRIX);

        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        zoomBy(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                        return true;
                    }
                });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (isZoomed()) {
                    fitToView();
                } else {
                    zoomBy(DOUBLE_TAP_ZOOM, e.getX(), e.getY());
                }
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (!isZoomed()) {
                    return false;
                }
                zoomMatrix.postTranslate(-distanceX, -distanceY);
                clampTranslation();
                setImageMatrix(zoomMatrix);
                return true;
            }
        });
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        super.setImageBitmap(bm);
        fitToView();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fitToView();
    }

    // Scale the picture so it fits the screen and centre it
    private void fitToView() {
        Drawable drawable = getDrawable();
        if (drawable == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        float drawableWidth = drawable.getIntrinsicWidth();
        float drawableHeight = drawable.getIntrinsicHeight();
        if (drawableWidth <= 0 || drawableHeight <= 0) {
            return;
        }
        fitScale = Math.min(getWidth() / drawableWidth, getHeight() / drawableHeight);
        zoomMatrix.reset();
        zoomMatrix.postScale(fitScale, fitScale);
        zoomMatrix.postTranslate(
                (getWidth() - drawableWidth * fitScale) / 2f,
                (getHeight() - drawableHeight * fitScale) / 2f);
        setImageMatrix(zoomMatrix);
    }

    private float currentScale() {
        zoomMatrix.getValues(matrixValues);
        return matrixValues[Matrix.MSCALE_X];
    }

    private boolean isZoomed() {
        return currentScale() > fitScale * 1.01f;
    }

    private void zoomBy(float factor, float focusX, float focusY) {
        if (getDrawable() == null) {
            return;
        }
        float current = currentScale();
        float target = Math.max(fitScale, Math.min(current * factor, fitScale * MAX_ZOOM));
        float applied = target / current;
        zoomMatrix.postScale(applied, applied, focusX, focusY);
        clampTranslation();
        setImageMatrix(zoomMatrix);
    }

    // Keep the picture from being dragged away from the screen edges
    private void clampTranslation() {
        Drawable drawable = getDrawable();
        if (drawable == null) {
            return;
        }
        tempRect.set(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        zoomMatrix.mapRect(tempRect);

        float dx = 0f;
        float dy = 0f;
        if (tempRect.width() <= getWidth()) {
            dx = (getWidth() - tempRect.width()) / 2f - tempRect.left;
        } else if (tempRect.left > 0) {
            dx = -tempRect.left;
        } else if (tempRect.right < getWidth()) {
            dx = getWidth() - tempRect.right;
        }
        if (tempRect.height() <= getHeight()) {
            dy = (getHeight() - tempRect.height()) / 2f - tempRect.top;
        } else if (tempRect.top > 0) {
            dy = -tempRect.top;
        } else if (tempRect.bottom < getHeight()) {
            dy = getHeight() - tempRect.bottom;
        }
        zoomMatrix.postTranslate(dx, dy);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (getDrawable() == null) {
            return super.onTouchEvent(event);
        }
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        // When zoomed (or pinching), the ViewPager2 must not steal the horizontal drag.
        // When not zoomed, it may, so the user can swipe to the next picture.
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(isZoomed() || event.getPointerCount() > 1);
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            performClick();
        }
        return true;
    }
}