package gis.hmap;

public class MapLoadedEvent {
    public double [] bounds;
    public double lat;
    public double lng;
    public String parkId;

    public MapLoadedEvent(String parkId, double [] bounds, double lat, double lng) {
        this.parkId = parkId;
        this.bounds = bounds;
        this.lat = lat;
        this.lng = lng;
    }
}
