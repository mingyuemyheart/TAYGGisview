package gis.hmap;

/**
 * Created by Ryan on 2018/9/22.
 */

public interface MarkerListener {
    void markerEvent(MarkerEvent me);
    void markerEvent(MarkerEvent[] mes);
}
