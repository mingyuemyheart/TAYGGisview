package gis.hmap;

import com.supermap.android.maps.Point2D;

import java.util.List;

/**
 * 获取路径规划数据
 */
public interface PathPlanDataListener {
    void pathPlanDataSuccess(List<Point2D> point2DS);
    void pathPlanDataFailed(String msg);
}
