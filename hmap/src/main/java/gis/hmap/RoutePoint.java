package gis.hmap;

import android.graphics.drawable.Drawable;

/**
 * Created by Ryan on 2018/9/29.
 */

public class RoutePoint {
    public double[] coords;
    public int color;
    public int width;
    public int opacity;
    public String buildingId;
    public String floorid;
    public Drawable marker;
    public int mkWidth;
    public int mkHeight;

    public RoutePoint(double[] coords, int color, String buildingId,
                      String floorid, int width, int opacity) {
        this.coords = coords;
        this.color = color;
        this.buildingId = buildingId;
        this.floorid = floorid;
        this.width = width;
        this.opacity = opacity;
        this.marker = null;
        this.mkWidth = 0;
        this.mkHeight = 0;
    }

    public RoutePoint(double[] coords, int color, String buildingId,
                      String floorid, int width, int opacity, Drawable marker, int mkWidth, int mkHeight) {
        this.coords = coords;
        this.color = color;
        this.buildingId = buildingId;
        this.floorid = floorid;
        this.width = width;
        this.opacity = opacity;
        this.marker = marker;
        this.mkWidth = mkWidth;
        this.mkHeight = mkHeight;
    }

    //获取路径规划数据
    public RoutePoint(double[] coords, String buildingId, String floorid) {
        this.coords = coords;
        this.buildingId = buildingId;
        this.floorid = floorid;
    }

}
