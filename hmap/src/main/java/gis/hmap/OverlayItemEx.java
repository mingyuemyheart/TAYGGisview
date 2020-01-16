package gis.hmap;

import com.supermap.android.maps.OverlayItem;
import com.supermap.android.maps.Point2D;

/**
 * Created by Ryan on 2018/9/22.
 */

public class OverlayItemEx extends OverlayItem {
    public Marker marker;

    public OverlayItemEx(Point2D point2D, String s, String s1, Marker marker) {
        super(point2D, s, s1);
        this.marker = marker;
    }
}
