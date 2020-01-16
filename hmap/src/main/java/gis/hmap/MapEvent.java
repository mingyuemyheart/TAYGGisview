package gis.hmap;

/**
 * Created by Ryan on 2018/10/29.
 */

public class MapEvent {
    public TargetEvent eventType;
    public int[] screenPos;
    public double[] geoPos;
    public String[] addrs;

    public MapEvent(TargetEvent eventType, int[] screenPos, double[] geoPos, String[] addrs) {
        this.eventType = eventType;
        this.screenPos = screenPos;
        this.geoPos = geoPos;
        this.addrs = addrs;
    }
}
