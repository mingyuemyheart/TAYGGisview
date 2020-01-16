package gis.hmap;

public class RouteEvent {
    public boolean success;
    public double totalLength;

    public RouteEvent(boolean success, double length) {
        this.success = success;
        this.totalLength = length;
    }
}
