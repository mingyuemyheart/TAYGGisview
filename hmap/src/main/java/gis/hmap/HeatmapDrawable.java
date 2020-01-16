package gis.hmap;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;


/**
 * Created by Ryan on 2018/10/29.
 */

class HeatmapDrawable extends Drawable {
    public double geoLeft;
    public double geoTop;
    public double geoRight;
    public double geoBottom;
    public HeatPoint[] points;
    private double radius;
    private Handler handler;
    private Rect heatZoneRect;
    private int left;
    private int top;
    private int right;
    private int bottom;
    private Bitmap mmbmp;
    private double maxHeat;
    private int[] colortab;

    public HeatmapDrawable(HeatPoint[] points, int radius, Handler handler) {
        this.points = points;
        this.radius = radius;
        this.handler = handler;

        double l, t, r, b;
        l = r = points[0].lng;
        t = b = points[0].lat;
        for(HeatPoint hp : points) {
            if (hp.lng < l) l = hp.lng;
            if (hp.lng > r) r = hp.lng;
            if (hp.lat < b) b = hp.lat;
            if (hp.lat > t) t = hp.lat;
        }
        this.geoLeft = l;
        this.geoTop = t;
        this.geoRight = r;
        this.geoBottom = b;

        colortab = new int[256];
        for (int i = 0; i < 256; i++)
            colortab[i] = Color.rgb(i/2, 255-i, i);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (mmbmp != null) {
//            canvas.save();
//            float sx = (float) mmbmp.getWidth() / (float) heatZoneRect.width();
//            float sy = (float) mmbmp.getHeight() / (float) heatZoneRect.height();
//            canvas.scale(sx, sy);
            canvas.drawBitmap(mmbmp, new Rect(0, 0, mmbmp.getWidth(), mmbmp.getHeight()), heatZoneRect, null);
//            canvas.restore();
        }
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        //
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    public void fitToMapView(final int left, final int top, final int right, final int bottom, boolean recalc) {
        boolean needReCalc = false;
        if (heatZoneRect == null)
            needReCalc = true;
        else if (right-left+(int)radius*2 != heatZoneRect.width())
            needReCalc = true;
        else if (bottom-top+(int)radius*2 != heatZoneRect.height())
            needReCalc = true;
        heatZoneRect = new Rect(left, top, right, bottom);
        heatZoneRect.inset(-(int)radius, -(int)radius);
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        if (needReCalc || recalc)
            Common.fixedThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    double[][] heatmap = new double[heatZoneRect.width()][heatZoneRect.height()];
                    maxHeat = 0;

                    for(HeatPoint point : points) {
                        int x0 = (int)((point.lng - geoLeft)/(geoRight - geoLeft)*(right - left)) + left;
                        int y0 = bottom - (int)((point.lat - geoBottom)/(geoTop - geoBottom)*(bottom - top));
                        int x1 = x0 - (int)radius;
                        int x2 = x0 + (int)radius;
                        int y1 = y0 - (int)radius;
                        int y2 = y0 + (int)radius;
                        double r2 = radius*radius;
                        double a = point.value;
                        double b = a / r2;
                        for (int x = x1; x <= x2; x++) {
                            for (int y = y1; y <= y2; y++) {
                                int x2_y2 = (x-x0)*(x-x0) + (y-y0)*(y-y0);
                                if (x2_y2 <= r2) {
                                    double val = a - b * x2_y2;
                                    try {
                                        if (val > heatmap[x - heatZoneRect.left][y - heatZoneRect.top])
                                            heatmap[x - heatZoneRect.left][y - heatZoneRect.top] = val;
                                        maxHeat = val > maxHeat ? val : maxHeat;
                                    } catch (Exception e) {
                                        Log.d("heatMap:", String.format("%d,%d,%d,%d - %d,%d,%d,%d", x, y, x0, y0,
                                                heatZoneRect.left, heatZoneRect.top, heatZoneRect.right, heatZoneRect.bottom));
                                    }
                                }
                            }
                        }
                    }

                    mmbmp = Bitmap.createBitmap(heatZoneRect.width(), heatZoneRect.height(), Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(mmbmp);
                    Paint paint = new Paint();
                    paint.setStyle(Paint.Style.FILL_AND_STROKE);
                    for (int i = 0; i < heatmap.length; i++) {
                        for (int j = 0; j < heatmap[i].length; j++) {
                            if (heatmap[i][j] == 0) continue;
                            int val = (int)(heatmap[i][j] / maxHeat * 255);
                            if (val < colortab.length) {
                                paint.setColor(colortab[val]);
                                paint.setAlpha((int)(Math.sqrt(val)*15));
                                canvas.drawPoint(i, j, paint);
                            }
                        }
                    }
                    Message msg = new Message();
                    msg.obj = null;
                    msg.what = Common.HEAT_MAP_CALC_END;
                    handler.sendMessage(msg);
                }
            });
    }
}
