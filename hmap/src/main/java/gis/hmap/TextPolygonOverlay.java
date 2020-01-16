package gis.hmap;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.text.TextPaint;

import com.supermap.android.maps.MapView;
import com.supermap.android.maps.Point2D;
import com.supermap.android.maps.PolygonOverlay;

/**
 * 文本overlay
 */
class TextPolygonOverlay extends PolygonOverlay {

    private double[] position;//位置
    private String content;//内容

    private int textColor = Color.BLACK;

    private TextPaint textPaint;

    public void setPosition(double[] position) {
        this.position = position;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setTextColor(int textColor) {
        this.textColor = textColor;
    }

    public TextPolygonOverlay(double[] position, String content, int textColor) {
        this.position = position;
        this.content = content;
        this.textColor = textColor;

        textPaint = new TextPaint();
        textPaint.setColor(textColor);
        textPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        textPaint.setAntiAlias(true);
    }

    @Override
    public void draw(Canvas canvas, MapView mapView, boolean shadow) {
        super.draw(canvas, mapView, shadow);
        Point point = mapView.getProjection().toPixels(new Point2D(position[1], position[0]), null);

        textPaint.setTextSize(mapView.getZoomLevel()*10);

        Rect rect = new Rect();
        textPaint.getTextBounds(content, 0, content.length(), rect);
        int textWidth = rect.width();
        int textHeight = rect.height();

        canvas.drawText(content, point.x-textWidth/2, point.y+textHeight/2, textPaint);
    }

}
