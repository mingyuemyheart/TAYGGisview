package gis.hmap;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import com.supermap.android.maps.MapView;
import com.supermap.android.maps.Overlay;
import com.supermap.android.maps.Point2D;

/**
 * Created by Ryan on 2018/10/18.
 */

class SimplePopupOverlay extends Overlay {
    private double[] coord;
    private double[] offset;
    private String content;
    private int width;
    private int height;
    private Object tag;

    public void setCoord(double[] coord) {
        this.coord = coord;
    }

    public void setOffset(double[] offset) {
        this.offset = offset;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void setTag(Object tag) {
        this.tag = tag;
    }

    public Object getTag() {
        return tag;
    }

    @Override
    public void draw(Canvas canvas, MapView mapView, boolean shadow) {
        super.draw(canvas, mapView, shadow);
        //消息框
        Point point = mapView.getProjection().toPixels(new Point2D(coord[1], coord[0]), null);
        RectF rect = new RectF(point.x, point.y, point.x+width, point.y+height);
        rect.offset((float) offset[0], (float) offset[1]);
        Paint bkpaint = new Paint();
        bkpaint.setARGB(255, 255, 255, 255);
        bkpaint.setStyle(Paint.Style.FILL_AND_STROKE);
        canvas.drawRoundRect(rect, 10, 10, bkpaint);
        //文字
        TextPaint tp = new TextPaint();
        tp.setColor(Color.BLACK);
        tp.setStyle(Paint.Style.FILL);
        tp.setTextSize(32);
        StaticLayout txtLayout = new StaticLayout(content, tp, (int)rect.width()- 10, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        canvas.save();
        canvas.translate(rect.left+ 5,rect.top);
        txtLayout.draw(canvas);
        canvas.restore();
    }
}
