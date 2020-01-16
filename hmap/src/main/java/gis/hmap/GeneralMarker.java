package gis.hmap;

import android.graphics.drawable.Drawable;

/**
 * 普通marker
 */
public class GeneralMarker extends Marker {

    public String imagePath;
    public Drawable image;

    public GeneralMarker() {

    }

    public GeneralMarker(double[] position, String markerId, String image, int width, int height, Object tag) {
        super(position, markerId, width, height, tag);
        this.image = null;
        this.imagePath = image;
        if (this.imagePath.startsWith("./")) {
            this.imagePath = Common.getHost() + "/gis/" + imagePath.substring(2);
        }
    }

    public GeneralMarker(double[] position, String markerId, Drawable image, int width, int height, Object tag) {
        super(position, markerId, width, height, tag);
        this.image = image;
        this.imagePath = null;
    }

}

