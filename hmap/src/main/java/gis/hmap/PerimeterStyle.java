package gis.hmap;

/**
 * Created by Ryan on 2018/10/18.
 */

public class PerimeterStyle {
    public int color;
    public int width;
    public int opacity;

    public PerimeterStyle() {
        //
    }

    public PerimeterStyle(int color, int width, int opacity) {
        this.color = color;
        this.width = width;
        this.opacity = opacity;
    }
}
