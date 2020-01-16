package gis.hmap;

/**
 * Created by Ryan on 2018/9/22.
 */

public class MarkerEvent {
    public TargetEvent eventType;
    public double[] position;
    public Marker marker;
    public int width;
    public int height;
    public Object tag;
    public String markerId;

    public MarkerEvent() {

    }

    public MarkerEvent(TargetEvent eventType, double[] pos, Marker marker, String markerId) {
        this.eventType = eventType;
        this.position = pos;
        this.marker = marker;
        this.width = marker.width;
        this.height = marker.height;
        this.tag = marker.tag;
        this.markerId = markerId;
    }
}
