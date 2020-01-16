package gis.hmap;

import android.graphics.Canvas;
import android.graphics.Point;
import android.view.View;

import com.supermap.android.maps.MapView;
import com.supermap.android.maps.Overlay;
import com.supermap.android.maps.Point2D;

/**
 * Created by Ryan on 2018/10/18.
 */

public class PopupOverlay extends Overlay {
    private double[] coord;
    private double[] offset;
    public View popView = null;
    private Object tag;

    public void setCoord(double[] coord) {
        this.coord = coord;
    }

    public void setOffset(double[] offset) {
        this.offset = offset;
    }

    public void setView(View view) {
        this.popView = view;
    }

    public void setTag(Object tag) {
        this.tag = tag;
    }

    public Object getTag() {
        return tag;
    }

    @Override
    public void draw(Canvas canvas, MapView mapView, boolean b) {
        super.draw(canvas, mapView, b);
        Point point = mapView.getProjection().toPixels(new Point2D(coord[1], coord[0]), null);
        point.offset((int)offset[0], (int)offset[1]);
        canvas.save();
        canvas.translate(point.x, point.y);
        popView.draw(canvas);
        canvas.restore();
    }
}
