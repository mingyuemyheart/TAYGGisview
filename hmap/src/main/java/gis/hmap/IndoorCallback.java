package gis.hmap;

import java.util.List;
import java.util.Map;

/**
 * 室内地图回调
 */
public interface IndoorCallback {
    void showIndoorSuccess(List<Map<String, String>> dataList);
}
