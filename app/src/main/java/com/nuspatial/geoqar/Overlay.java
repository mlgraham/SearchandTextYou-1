package com.nuspatial.geoqar;

import android.app.FragmentTransaction;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import java.lang.Math;

/**
 * Created by michael on 2/25/17.
 */

public class Overlay extends View implements SensorMixerCallback {

    private Context mContext;
    public List<PointOfInterest> mRenderPoints;

    class PointXY {
        float x;
        float y;

        public PointXY(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public boolean isClose(float xx, float yy) {
            return ((xx-x) * (xx-x) + (yy-y) * (yy-y)) < 2000;
        }
    }

    private List<PointXY> mDrawnPoints;

    public GeoPoint mCurLoc;

    private float[] proj_matrix;
    private float[] inv_proj_matrix;
    private float[] dist_params;
    private float[] view_matrix;

    private View.OnTouchListener handleTouch = new View.OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {

            int x = (int) event.getX();
            int y = (int) event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_UP:
                    for (int i = 0; i < mDrawnPoints.size(); ++i) {
                        if(mDrawnPoints.get(i).isClose(x, y)) {
                            Log.i("TAG", mRenderPoints.get(i).mStatus);
                            if(mPOIClick != null) {
                                mPOIClick.onClick(mRenderPoints.get(i));
                            }
                        }
                    }
                    break;
            }

            return true;
        }
    };

    private PointOfInterestClickInterface mPOIClick;

    public void setCallback(PointOfInterestClickInterface callback) {
        mPOIClick = callback;
    }

    public Overlay(Context context) {
        super(context);
        mContext = context;
        // Haha :shipit:
        proj_matrix = new float[]{ 1821.21508f, 0.0f, 944.8632f, 0.0f,
                0.0f, 1835.043f, 565.390f, 0.0f,
                0.0f, 0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f};

        inv_proj_matrix = new float[16];
        Matrix.invertM(inv_proj_matrix, 0, proj_matrix, 0);

        dist_params = new float[] { 0.28827641178f, -0.587596880385f, -0.00131490053239f, -0.00336878635936f, -0.547512389064f };

        view_matrix = new float[]{ 1.0f, 0.0f, 0.0f, 0.0f,
                                   0.0f, 1.0f, 0.0f, 0.0f,
                                   0.0f, 0.0f, 1.0f, 0.0f,
                                   0.0f, 0.0f, 0.0f, 1.0f};

        mDrawnPoints = new ArrayList<PointXY>();
        setOnTouchListener(this.handleTouch);
    }


    @Override
    public void onLocation(double[] location) {
     setCurrentLocation(new GeoPoint(location[0], location[1], location[2]));
    }

    @Override
    public void onMatrix(float[] matrix) {
        this.view_matrix = matrix;
        this.invalidate();
    }

    public void setRenderPoints(List<PointOfInterest> pts) {
        mRenderPoints = pts;
    }

    public void setCurrentLocation(GeoPoint pt) {
        mCurLoc = pt;
        this.invalidate();
    }

    public float[] drawPoint(double[] ned) {
        float[] pt_c = new float[4];
        float[] ned2 = new float[]{(float) ned[0], (float) ned[1], (float) ned[2], 1.0f};
        Matrix.multiplyMV(pt_c, 0, view_matrix, 0, ned2, 0);
        float x_p = pt_c[0]/pt_c[2];
        float y_p = pt_c[1]/pt_c[2];
        float v = proj_matrix[0] * x_p + proj_matrix[2];
        float u = proj_matrix[5] * y_p + proj_matrix[6];
        return new float[] {u, v};
    }

    public float[] drawPoint(double n, double e, double d) {
        float[] pt_c = new float[4];
        float[] ned2 = new float[]{(float) n, (float) e, (float) d, 1.0f};
        Matrix.multiplyMV(pt_c, 0, view_matrix, 0, ned2, 0);
        float x_p = pt_c[0]/pt_c[2];
        float y_p = pt_c[1]/pt_c[2];
        float v = proj_matrix[0] * x_p + proj_matrix[2];
        float u = proj_matrix[5] * y_p + proj_matrix[6];
        return new float[] {u, v};
    }


    @Override
    public void onDraw(Canvas canvas) {

        // General flow:
        // convert current location into ecef cartesian.
        // foreach point to be drawn
        //   convert point location into ecef cartesian.
        //   take difference of point and origin in cartesian space to get vector
        //   rotate that vector into a local tangent plane aligned N-E-D
        //   rotate that vector into the camera coordinate frame using the orientation sensor
        //   project that vector into the camera using the camera intrinsics (K) matrix.
        //  Bonus android: Rescale the point position based on the current canvas size.

        super.onDraw(canvas);

        //Log.i("Overlay", "onDraw");

        if(mCurLoc == null || mRenderPoints.size() == 0) {
            return;
        }

        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth((float)5.0);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextSize(40);

        mDrawnPoints.clear();

        for(int i = 0; i < mRenderPoints.size(); ++i) {
            double[] ned;

            Log.i("Overlay",
                    Double.toString(mCurLoc.mLat) + " " + Double.toString(mCurLoc.mLon) + " " + Double.toString(mCurLoc.mAlt));


            if(mRenderPoints.get(i).mPoint.mAlt < 0) {
                GeoPoint pt = new GeoPoint ( mRenderPoints.get(i).mPoint.mLat,
                                             mRenderPoints.get(i).mPoint.mLon,
                                              mCurLoc.mAlt + 1.0);


                Log.i("Overlay",
                        "no alt: " +
                        Double.toString(pt.mLat) + " " + Double.toString(pt.mLon) + " " + Double.toString(pt.mAlt));
                ned = pt.toNED(mCurLoc);
            } else {
                GeoPoint pt = mRenderPoints.get(i).mPoint;
                 Log.i("Overlay",
                        "with alt: " + Double.toString(pt.mLat) + " " + Double.toString(pt.mLon) + " " + Double.toString(pt.mAlt));
                ned = mRenderPoints.get(i).mPoint.toNED(mCurLoc);
            }

            double[] ned3 = new double[] { ned[1], -ned[0], ned[2] };

            ned = ned3;

            /*
            Log.i("Overlay", "NED: " + Double.toString(ned[0]) + " " +
                    Double.toString(ned[1]) + " " +
                    Double.toString(ned[2]));
                    *

            /*
            Log.i("Overlay", Float.toString(pt_c[0]) + " " +
                             Float.toString(pt_c[1]) + " " +
                             Float.toString(pt_c[2]) + " " +
                             Float.toString(pt_c[3]));
                             */


            float[] pt_c = new float[4];
            float[] ned2 = new float[]{(float) ned[0], (float) ned[1], (float) ned[2], 1.0f};
            Log.i("Render", String.format("%3.4f %3.4f %3.4f", ned[0], ned[1], ned[2]));

            Matrix.multiplyMV(pt_c, 0, view_matrix, 0, ned2, 0);
            /*
            n e s w
            e s w n
            w = n
*/
            // Only render stuff that's in front of the camera
            if(pt_c[2] > 0) {
                Log.i("Render", "Rendering " + Integer.toString(i));
                if (mRenderPoints.get(i).mStatus.equals("Black")) paint.setColor(Color.DKGRAY);
                if (mRenderPoints.get(i).mStatus.equals("Red")) paint.setColor(Color.RED);
                if (mRenderPoints.get(i).mStatus.equals("Yellow")) paint.setColor(Color.YELLOW);
                if (mRenderPoints.get(i).mStatus.equals("Green")) paint.setColor(Color.GREEN);

                {
                    //Log.i("Overlay", "x_p: " + Float.toString(x_p) + "   y_p: " + Float.toString(y_p));
                    float[] uv = drawPoint(ned);

                    float draw_x = (uv[0] / 1920.0f) * canvas.getWidth();
                    float draw_y = (uv[1] / 1080.0f) * canvas.getHeight();
                    double dist = Math.sqrt(ned[0] * ned[0] + ned[1] * ned[1] + ned[2] * ned[2]);
                    mDrawnPoints.add(new PointXY(draw_x, draw_y));
                    canvas.drawText(mRenderPoints.get(i).mStatus, draw_x + 100, draw_y, paint);
                    canvas.drawText(String.format("%3.0f m", dist), draw_x + 100, draw_y+50, paint);
                }

                {
                    float[] uv1 = drawPoint(ned[0] + 1, ned[1], ned[2]);
                    float draw_x1 = (uv1[0] / 1920.0f) * canvas.getWidth();
                    float draw_y1 = (uv1[1] / 1080.0f) * canvas.getHeight();
                    float[] uv2 = drawPoint(ned[0] - 1, ned[1], ned[2]);
                    float draw_x2 = (uv2[0] / 1920.0f) * canvas.getWidth();
                    float draw_y2 = (uv2[1] / 1080.0f) * canvas.getHeight();

                    if (uv1[0] > 0.0 && uv2[0] > 0.0 && uv1[1] > 0.0 && uv1[1] > 0.0) {
                        //Log.i("Render", String.format("%3.0f %3.0f %3.0f %3.0f", draw_x1, draw_y1, draw_x2, draw_y2));
                        canvas.drawLine(draw_x1, draw_y1, draw_x2, draw_y2, paint);
                    }
                }

                {
                    float[] uv1 = drawPoint(ned[0], ned[1] + 1, ned[2]);
                    float draw_x1 = (uv1[0] / 1920.0f) * canvas.getWidth();
                    float draw_y1 = (uv1[1] / 1080.0f) * canvas.getHeight();
                    float[] uv2 = drawPoint(ned[0], ned[1] - 1, ned[2]);
                    float draw_x2 = (uv2[0] / 1920.0f) * canvas.getWidth();
                    float draw_y2 = (uv2[1] / 1080.0f) * canvas.getHeight();

                    if (uv1[0] > 0.0 && uv2[0] > 0.0 && uv1[1] > 0.0 && uv1[1] > 0.0) {
                        //Log.i("Render", String.format("%3.0f %3.0f %3.0f %3.0f", draw_x1, draw_y1, draw_x2, draw_y2));
                        canvas.drawLine(draw_x1, draw_y1, draw_x2, draw_y2, paint);
                    }
                }

                {
                    float[] uv1 = drawPoint(ned[0], ned[1], ned[2] + 1);
                    float draw_x1 = (uv1[0] / 1920.0f) * canvas.getWidth();
                    float draw_y1 = (uv1[1] / 1080.0f) * canvas.getHeight();
                    float[] uv2 = drawPoint(ned[0], ned[1], ned[2] - 1);
                    float draw_x2 = (uv2[0] / 1920.0f) * canvas.getWidth();
                    float draw_y2 = (uv2[1] / 1080.0f) * canvas.getHeight();

                    if (uv1[0] > 0.0 && uv2[0] > 0.0 && uv1[1] > 0.0 && uv1[1] > 0.0) {
                        //Log.i("Render", String.format("%3.0f %3.0f %3.0f %3.0f", draw_x1, draw_y1, draw_x2, draw_y2));
                        canvas.drawLine(draw_x1, draw_y1, draw_x2, draw_y2, paint);
                    }
                }


            } else {
                mDrawnPoints.add(new PointXY(1e8f,1e8f));
            }

        }
    }
}
