package gis.hmap;

import com.supermap.android.maps.Point2D;

import java.util.HashMap;
import java.util.List;

/**
 * Created by Ryan on 2019/6/4.
 */

public class ModelData {
    public String key;
    public List<List<Point2D>> outline;
    public List<List<Point2D>> geometry;
    public HashMap<String, String> features;

    public ModelData(String key, List<List<Point2D>> outline, List<List<Point2D>> geometry, HashMap<String, String> features) {
        this.key = key;
        this.outline = outline;
        this.geometry = geometry;
        this.features = features;
    }
}
