package gis.hmap;

/**
 * Created by Ryan on 2018/9/22.
 */

public class ZoomEvent {
    public Zoom eventType;
    public int level;

    public ZoomEvent() {

    }

    public ZoomEvent(Zoom eventType, int level) {
        this.eventType = eventType;
        this.level = level;
    }
}
