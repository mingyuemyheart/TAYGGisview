package gis.hmap;

/**
 * Created by Ryan on 2018/9/21.
 */

public class Marker {
    public double[] position;
    public String markerId;
    public int width;
    public int height;
    public Object tag;

    public Marker() {

    }

    public Marker(double[] position, String markerId, int width, int height, Object tag) {
        this.position = position;
        this.markerId = markerId;
        this.width = width;
        this.height = height;
        this.tag = tag;
    }
}