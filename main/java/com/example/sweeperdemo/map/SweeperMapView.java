package com.example.sweeperdemo.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;


import com.sweeper.demo.set.R;

import java.util.List;

/**
 * Pinch-zoom occupancy map with path, dock and robot overlays.
 */
public class SweeperMapView extends View {

    private static final int[] ROOM_COLORS = {
            0xFFE8DCC8, 0xFFD4E8D0, 0xFFD5E4F2, 0xFFF3D9C8,
            0xFFE4D5F0, 0xFFF0E6C8, 0xFFCDE8E2, 0xFFF2D5DC
    };

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint pathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint navPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint robotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix drawMatrix = new Matrix();
    private final GestureDetector gestureDetector;
    private final ScaleGestureDetector scaleDetector;

    private Bitmap occupancy;
    private SweeperMapModel model;
    private float scale = 1f;
    private float transX;
    private float transY;
    private boolean fitted;

    public SweeperMapView(Context context) {
        this(context, null);
    }

    public SweeperMapView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SweeperMapView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setBackgroundColor(ContextCompat.getColor(context, R.color.sweeper_map_bg));
        pathPaint.setStyle(Paint.Style.STROKE);
        pathPaint.setStrokeCap(Paint.Cap.ROUND);
        pathPaint.setStrokeJoin(Paint.Join.ROUND);
        pathPaint.setColor(ContextCompat.getColor(context, R.color.sweeper_path));
        pathPaint.setStrokeWidth(1.6f);
        navPaint.set(pathPaint);
        navPaint.setColor(ContextCompat.getColor(context, R.color.sweeper_nav_path));
        navPaint.setStrokeWidth(1.2f);
        robotPaint.setColor(ContextCompat.getColor(context, R.color.sweeper_robot));
        dockPaint.setColor(ContextCompat.getColor(context, R.color.sweeper_charger));
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                transX -= distanceX;
                transY -= distanceY;
                invalidate();
                return true;
            }
        });
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                float next = Math.max(0.4f, Math.min(12f, scale * factor));
                float focusX = detector.getFocusX();
                float focusY = detector.getFocusY();
                transX = focusX - (focusX - transX) * (next / scale);
                transY = focusY - (focusY - transY) * (next / scale);
                scale = next;
                invalidate();
                return true;
            }
        });
    }

    /**
     * Replace occupancy + overlays and fit the map on first data.
     *
     * @param next parsed model
     */
    public void setModel(@Nullable SweeperMapModel next) {
        this.model = next;
        if (occupancy != null && !occupancy.isRecycled()) {
            occupancy.recycle();
            occupancy = null;
        }
        if (next != null && next.hasGrid()) {
            occupancy = renderOccupancy(next);
            fitted = false;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (occupancy == null || occupancy.isRecycled()) {
            return;
        }
        if (!fitted && getWidth() > 0 && getHeight() > 0) {
            fitToView();
            fitted = true;
        }
        drawMatrix.reset();
        drawMatrix.postScale(scale, scale);
        drawMatrix.postTranslate(transX, transY);
        canvas.save();
        canvas.concat(drawMatrix);
        canvas.drawBitmap(occupancy, 0, 0, bitmapPaint);
        drawPolyline(canvas, model == null ? null : model.path, pathPaint);
        drawPolyline(canvas, model == null ? null : model.navPath, navPaint);
        if (model != null && model.hasPile) {
            canvas.drawCircle(model.pileX + 0.5f, model.pileY + 0.5f, 3.2f, dockPaint);
        }
        float[] robot = lastPoint();
        if (robot != null) {
            canvas.drawCircle(robot[0], robot[1], 2.6f, robotPaint);
        }
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean scaleHandled = scaleDetector.onTouchEvent(event);
        boolean panHandled = gestureDetector.onTouchEvent(event);
        return scaleHandled || panHandled || super.onTouchEvent(event);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (occupancy != null && !occupancy.isRecycled()) {
            occupancy.recycle();
            occupancy = null;
        }
    }

    private void fitToView() {
        float bw = occupancy.getWidth();
        float bh = occupancy.getHeight();
        if (bw <= 0 || bh <= 0) {
            return;
        }
        float padding = 32f;
        scale = Math.min((getWidth() - padding) / bw, (getHeight() - padding) / bh);
        scale = Math.max(0.4f, scale);
        transX = (getWidth() - bw * scale) / 2f;
        transY = (getHeight() - bh * scale) / 2f;
    }

    private void drawPolyline(Canvas canvas, List<float[]> points, Paint paint) {
        if (points == null || points.size() < 2) {
            return;
        }
        Path path = new Path();
        float[] first = points.get(0);
        path.moveTo(first[0], first[1]);
        for (int i = 1; i < points.size(); i++) {
            float[] p = points.get(i);
            path.lineTo(p[0], p[1]);
        }
        canvas.drawPath(path, paint);
    }

    @Nullable
    private float[] lastPoint() {
        if (model == null) {
            return null;
        }
        if (!model.path.isEmpty()) {
            return model.path.get(model.path.size() - 1);
        }
        if (!model.navPath.isEmpty()) {
            return model.navPath.get(model.navPath.size() - 1);
        }
        return null;
    }

    private Bitmap renderOccupancy(SweeperMapModel map) {
        int w = map.width;
        int h = map.height;
        int[] colors = new int[w * h];
        int wall = ContextCompat.getColor(getContext(), R.color.sweeper_wall);
        int charger = ContextCompat.getColor(getContext(), R.color.sweeper_charger);
        for (int i = 0; i < colors.length; i++) {
            int v = map.pixels[i] & 0xFF;
            if (v == 0) {
                colors[i] = Color.TRANSPARENT;
            } else if (v == 1) {
                colors[i] = wall;
            } else if (v == 127 || v == 255) {
                colors[i] = charger;
            } else {
                colors[i] = ROOM_COLORS[v % ROOM_COLORS.length];
            }
        }
        return Bitmap.createBitmap(colors, w, h, Bitmap.Config.ARGB_8888);
    }
}
