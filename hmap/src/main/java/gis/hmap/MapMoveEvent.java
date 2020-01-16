package gis.hmap;

public class MapMoveEvent {
    public double [] bounds;
    public double lat;
    public double lng;

    public MapMoveEvent(double [] bounds, double lat, double lng) {
        this.bounds = bounds;
        this.lat = lat;
        this.lng = lng;
    }
}
