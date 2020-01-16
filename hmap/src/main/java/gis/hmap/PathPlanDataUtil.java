package gis.hmap;

import android.text.TextUtils;
import android.util.Log;

import com.supermap.android.commons.EventStatus;
import com.supermap.android.maps.BoundingBox;
import com.supermap.android.maps.MapView;
import com.supermap.android.maps.Point2D;
import com.supermap.android.networkAnalyst.FindPathParameters;
import com.supermap.android.networkAnalyst.FindPathResult;
import com.supermap.android.networkAnalyst.FindPathService;
import com.supermap.android.networkAnalyst.TransportationAnalystParameter;
import com.supermap.android.networkAnalyst.TransportationAnalystResultSetting;
import com.supermap.services.components.commontypes.Path;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

/**
 * 获取路径规划数据
 */
public class PathPlanDataUtil {

    public static class PathPlanData {
        public RoutePoint start;
        public RoutePoint end;
        public List<Path[]> route;
        public List<String> range;
        public List<List<WayPoint>> wayPoints;

        public PathPlanData(RoutePoint start, RoutePoint end) {
            this.start = start;
            this.end = end;
            route = new ArrayList<>();
            range = new ArrayList<>();
        }
    }

    public static void excutePathService(MapView mapView, RoutePoint start, RoutePoint end, PathPlanDataListener listener) {
        Common.fixedThreadPool.execute(new PathServiceRunnable(mapView, start, end, listener));
    }

    private static class PathServiceRunnable implements Runnable {
        private List<WayPoint> nodes;
        private PathPlanDataListener listener;
        private HashMap<String, List<Point2D>> inout;
        private HashMap<String, List<Point2D>> lift;
        private RoutePoint start;
        private RoutePoint end;

        public PathServiceRunnable(MapView mapView, RoutePoint start, RoutePoint end, PathPlanDataListener listener) {
            BoundingBox boundingBox = mapView.getBounds();
            nodes = new ArrayList<>();
            if (start != null && start.coords != null && start.coords.length >= 2) {
                if (boundingBox != null) {
                    if (boundingBox.contains(new Point2D(start.coords[1], start.coords[0])))
                        nodes.add(new WayPoint(start.coords[1], start.coords[0], start.buildingId, start.floorid));
                } else {
                    nodes.add(new WayPoint(start.coords[1], start.coords[0], start.buildingId, start.floorid));
                }
            }
            if (end != null && end.coords != null && end.coords.length >= 2) {
                if (boundingBox != null) {
                    if (boundingBox.contains(new Point2D(end.coords[1], end.coords[0])))
                        nodes.add(new WayPoint(end.coords[1], end.coords[0], end.buildingId, end.floorid));
                } else {
                    nodes.add(new WayPoint(end.coords[1], end.coords[0], end.buildingId, end.floorid));
                }
            }
            this.listener = listener;
            this.start = start;
            this.end = end;
            inout = new HashMap<>();
            lift = new HashMap<>();
        }

        @Override
        public void run() {
            //预处理坐标点
            List<String> indoors = new ArrayList<>();
            for (WayPoint p : nodes) {
                if (!TextUtils.isEmpty(p.building)) {
                    if (!indoors.contains(p.building))
                        indoors.add(p.building);
                } else if (!TextUtils.isEmpty(p.floor)) {
                    if (!indoors.contains("BASEMENT"))
                        indoors.add("BASEMENT");
                }
            }
            List<List<WayPoint>> realNodes = putInoutAndLift(indoors);

            TransportationAnalystResultSetting resultSetting = new TransportationAnalystResultSetting();
            resultSetting.returnEdgeFeatures = true;
            resultSetting.returnEdgeGeometry = true;
            resultSetting.returnEdgeIDs = true;
            resultSetting.returnNodeFeatures = true;
            resultSetting.returnNodeGeometry = true;
            resultSetting.returnNodeIDs = true;
            resultSetting.returnPathGuides = true;
            resultSetting.returnRoutes = true;

            TransportationAnalystParameter analystParameter = new TransportationAnalystParameter();
            analystParameter.resultSetting = resultSetting;
            analystParameter.weightFieldName = "smLength";

            FindPathParameters params = new FindPathParameters();
            params.parameter = analystParameter;
            PathPlanData result = new PathPlanData(start, end);
            result.wayPoints = realNodes;

            for (List<WayPoint> waysec : realNodes) {
                if (waysec == null || waysec.size() <= 0)
                    continue;

                String range = "Outdoor";
                WayPoint s = waysec.get(0);
                if (!TextUtils.isEmpty(s.floor)) {
                    if (TextUtils.isEmpty(s.building))
                        range = "Basement:"+s.floor;
                    else
                        range = s.building+":"+s.floor;
                }
                List<Point2D> pts = new ArrayList<>();
                for (WayPoint pt : waysec) {
                    if (pt != null && pt.point != null) {
                        pts.add(pt.point);
                    }
                }
                params.nodes = pts.toArray();

                try {
                    FindPathService path = new FindPathService(Common.getHost() + Common.TRANSPORT_URL(waysec.get(0).floor));
                    MyFindPathEventListener listener = new MyFindPathEventListener();
                    path.process(params, listener);
                    try {
                        listener.waitUntilProcessed();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    FindPathResult pathResult = listener.getReult();
                    if (pathResult != null) {
                        result.route.add(pathResult.pathList);
                        result.range.add(range);
                    } else
                        Common.getLogger(null).log(Level.INFO, "FindPathService: No result");
                } catch (Exception ex) {
                    Log.e("FindPathService", ex.getMessage());
                    Common.getLogger(null).log(Level.INFO, "FindPathService: " + ex.getMessage());
                }
            }

            List<Point2D> point2DS = new ArrayList<>();
            point2DS.add(new Point2D(start.coords[1], start.coords[0]));
            // 绘制室外路线
            for (int i = 0; i < result.range.size(); i++) {
                com.supermap.services.components.commontypes.Path[] paths = result.route.get(i);
                if (paths == null)
                    continue;
                for (com.supermap.services.components.commontypes.Path path : paths) {
                    if (path.route != null && path.route.points != null && path.route.points.length > 0) {
                        for (com.supermap.services.components.commontypes.Point2D point : path.route.points) {
                            point2DS.add(new Point2D(point.x, point.y));
                        }
                    }
                }
            }
            point2DS.add(new Point2D(end.coords[1], end.coords[0]));

            if (listener != null) {
                if (point2DS != null && point2DS.size() > 0) {
                    listener.pathPlanDataSuccess(point2DS);
                } else {
                    listener.pathPlanDataFailed("查询失败");
                }
            }
        }

        private List<List<WayPoint>> putInoutAndLift(List<String> indoors) {
            List<List<WayPoint>> realNodes;
            if (indoors != null && indoors.size() > 0) {
                if (indoors.contains("BASEMENT")) {
                    List<QueryUtils.BuildingResult> buildings = QueryUtils.getBuildings();
                    for (QueryUtils.BuildingResult b : buildings) {
                        String buildingId = "";
                        for (int i=0; i<b.feature.fieldNames.length; i++) {
                            if (b.feature.fieldNames[i].equalsIgnoreCase("buildingid"))
                                buildingId = b.feature.fieldValues[i];
                        }
                        List<ObjectInfo> res = QueryUtils.getObjects(
                                "Entry",
                                String.format("DevType like '%s' and buildingid='%s'", "%电梯%", buildingId));
                        if (res != null) {
                            List<Point2D> point2DS = new ArrayList<>();
                            for (ObjectInfo obj : res) {
                                Point2D point = new Point2D(obj.getNumParam("SMX"), obj.getNumParam("SMY"));
                                point2DS.add(point);
                            }
                            lift.put(buildingId, point2DS);
                        }
                        res = QueryUtils.getObjects(
                                "Entry",
                                String.format("DevType like '%s' and buildingid='%s'", "%出入口%", buildingId));
                        if (res != null) {
                            List<Point2D> point2DS = new ArrayList<>();
                            for (ObjectInfo obj : res) {
                                Point2D point = new Point2D(obj.getNumParam("SMX"), obj.getNumParam("SMY"));
                                point2DS.add(point);
                            }
                            inout.put(buildingId, point2DS);
                        }
                    }
                } else {
                    for (String buildingId : indoors) {
                        List<ObjectInfo> res = QueryUtils.getObjects(
                                "Entry",
                                String.format("DevType like '%s' and buildingid='%s'", "%电梯%", buildingId));
                        if (res != null) {
                            List<Point2D> point2DS = new ArrayList<>();
                            for (ObjectInfo obj : res) {
                                Point2D point = new Point2D(obj.getNumParam("SMX"), obj.getNumParam("SMY"));
                                point2DS.add(point);
                            }
                            lift.put(buildingId, point2DS);
                        }
                        res = QueryUtils.getObjects(
                                "Entry",
                                String.format("DevType like '%s' and buildingid='%s'", "%出入口%", buildingId));
                        if (res != null) {
                            List<Point2D> point2DS = new ArrayList<>();
                            for (ObjectInfo obj : res) {
                                Point2D point = new Point2D(obj.getNumParam("SMX"), obj.getNumParam("SMY"));
                                point2DS.add(point);
                            }
                            inout.put(buildingId, point2DS);
                        }
                    }
                }
                realNodes = insertStageInoutLift();
            } else {
                realNodes = new ArrayList<>();
                realNodes.add(nodes);
            }

            return realNodes;
        }

        private List<List<WayPoint>> insertStageInoutLift() {
            List<List<WayPoint>> newNodes = new ArrayList<>();
            for (int i = 0; i < nodes.size()-1; i++) {
                WayPoint A = nodes.get(i);
                WayPoint B = nodes.get(i+1);

                List<WayPoint> part;
                try {
                    //查询A楼出入口
                    WayPoint nodeA = findNearByInout(A, A.building);
                    WayPoint nodeA2 = new WayPoint(nodeA.point.x, nodeA.point.y, nodeA.building, "", nodeA.catalog);
                    //查询B楼出入口
                    WayPoint nodeB = findNearByInout(B, B.building);
                    WayPoint nodeB2 = new WayPoint(nodeB.point.x, nodeB.point.y, nodeB.building, "", nodeB.catalog);

                    //查询A楼电梯
                    WayPoint liftA = findNearByLift(A, A.building);
                    //查询B楼电梯
                    WayPoint liftB = findNearByLift(B, B.building);

                    if (A.building.equalsIgnoreCase(B.building)) {//相同楼栋
                        if (A.floor.equalsIgnoreCase(B.floor)) {//相同楼栋、相同楼层
                            part = new ArrayList<>();
                            part.add(A);
                            part.add(B);
                            newNodes.add(part);
                        } else {//相同楼栋、不同楼层
                            part = new ArrayList<>();
                            part.add(A);
                            part.add(liftA);
                            newNodes.add(part);

                            part = new ArrayList<>();
                            part.add(new WayPoint(liftA.point.x, liftA.point.y, liftA.building, B.floor, liftA.catalog));
                            part.add(B);
                            newNodes.add(part);
                        }
                    } else {//不同楼栋
                        if (A.floor.equalsIgnoreCase(B.floor)) {//不同楼栋、相同楼层
                            if (A.floor.equalsIgnoreCase("F1") || B.floor.equalsIgnoreCase("F1")) {//都是F1
                                part = new ArrayList<>();
                                part.add(A);
                                part.add(nodeA);
                                newNodes.add(part);

                                part = new ArrayList<>();
                                part.add(nodeA);
                                part.add(nodeA2);
                                newNodes.add(part);

                                part = new ArrayList<>();
                                part.add(nodeA2);
                                part.add(nodeB);
                                newNodes.add(part);

                                part = new ArrayList<>();
                                part.add(nodeB);
                                part.add(B);
                                newNodes.add(part);
                            } else {//都是非F1的同一层楼
                                part = new ArrayList<>();
                                part.add(A);
                                part.add(liftA);
                                newNodes.add(part);

                                part = new ArrayList<>();
                                part.add(liftA);
                                part.add(nodeA);
                                newNodes.add(part);

                                part = new ArrayList<>();
                                part.add(nodeA);
                                part.add(nodeA2);
                                newNodes.add(part);

                                part = new ArrayList<>();
                                part.add(nodeA2);
                                part.add(nodeB);
                                newNodes.add(part);

                                part = new ArrayList<>();
                                part.add(nodeB);
                                part.add(new WayPoint(nodeB.point.x, nodeB.point.y, nodeB.building, "F1", nodeB.catalog));
                                newNodes.add(part);

                                part = new ArrayList<>();
                                part.add(new WayPoint(nodeB.point.x, nodeB.point.y, nodeB.building, "F1", nodeB.catalog));
                                part.add(new WayPoint(liftB.point.x, liftB.point.y, liftB.building, "F1", liftB.catalog));
                                newNodes.add(part);

                                part = new ArrayList<>();
                                part.add(new WayPoint(liftB.point.x, liftB.point.y, liftB.building, "F1", liftB.catalog));
                                part.add(new WayPoint(liftB.point.x, liftB.point.y, liftB.building, B.floor, liftB.catalog));
                                newNodes.add(part);

                                part = new ArrayList<>();
                                part.add(new WayPoint(liftB.point.x, liftB.point.y, liftB.building, B.floor, liftB.catalog));
                                part.add(B);
                                newNodes.add(part);
                            }
                        } else {//不同楼栋、不同楼层
                            if (A.floor.equalsIgnoreCase("F1") && !B.floor.equalsIgnoreCase("F1")) {
                                part = new ArrayList<>();
                                part.add(A);
                                part.add(nodeA);
                                newNodes.add(part);

                                part = new ArrayList<>();
                                part.add(nodeA);
                                part.add(nodeA2);
                                newNodes.add(part);

                                part = new ArrayList<>();
                                part.add(nodeA2);
                                part.add(nodeB);
                                newNodes.add(part);

                                part = new ArrayList<>();
                                part.add(nodeB);
                                part.add(new WayPoint(nodeB.point.x, nodeB.point.y, nodeB.building, A.floor, nodeB.catalog));
                                newNodes.add(part);

                                part = new ArrayList<>();
                                part.add(new WayPoint(nodeB.point.x, nodeB.point.y, nodeB.building, A.floor, nodeB.catalog));
                                part.add(new WayPoint(liftB.point.x, liftB.point.y, liftB.building, A.floor, liftB.catalog));
                                newNodes.add(part);

                                part = new ArrayList<>();
                                part.add(new WayPoint(liftB.point.x, liftB.point.y, liftB.building, A.floor, liftB.catalog));
                                part.add(liftB);
                                newNodes.add(part);

                                part = new ArrayList<>();
                                part.add(liftB);
                                part.add(B);
                                newNodes.add(part);
                            } else if (!A.floor.equalsIgnoreCase("F1") && B.floor.equalsIgnoreCase("F1")) {
                                part = new ArrayList<>();
                                part.add(A);
                                part.add(liftA);
                                newNodes.add(part);

                                part = new ArrayList<>();
                                part.add(liftA);
                                part.add(new WayPoint(liftA.point.x, liftA.point.y, liftA.building, B.floor, liftA.catalog));
                                newNodes.add(part);

                                part = new ArrayList<>();
                                part.add(new WayPoint(liftA.point.x, liftA.point.y, liftA.building, B.floor, liftA.catalog));
                                part.add(new WayPoint(nodeA.point.x, nodeA.point.y, nodeA.building, B.floor, nodeA.catalog));
                                newNodes.add(part);

                                part = new ArrayList<>();
                                part.add(new WayPoint(nodeA.point.x, nodeA.point.y, nodeA.building, B.floor, nodeA.catalog));
                                part.add(nodeA);
                                newNodes.add(part);

                                part = new ArrayList<>();
                                part.add(nodeA);
                                part.add(nodeA2);
                                newNodes.add(part);

                                part = new ArrayList<>();
                                part.add(nodeA2);
                                part.add(nodeB);
                                newNodes.add(part);

                                part = new ArrayList<>();
                                part.add(nodeB);
                                part.add(B);
                                newNodes.add(part);
                            }
                        }
                    }
                } catch (Exception ex) {
                    Log.e("insertStageInoutLift", ex.getMessage());
                }
            }
            return newNodes;
        }

        private WayPoint findNearByInout(WayPoint p, String building) {
            WayPoint result = null;
            double d = 0;
            if (TextUtils.isEmpty(building)) {
                for (String build : inout.keySet()) {
                    List<Point2D> inouts = inout.get(build);
                    for (Point2D point2D : inouts) {
                        if (result == null) {
                            result = new WayPoint(point2D.x, point2D.y, build, p.floor, "InOut");
                            d = Math.pow(p.point.x - point2D.x, 2) + Math.pow(p.point.y - point2D.y, 2);
                        } else {
                            double d1 = Math.pow(p.point.x - point2D.x, 2) + Math.pow(p.point.y - point2D.y, 2);
                            if (d1 < d) {
                                d = d1;
                                result.building = build;
                                result.point.x = point2D.x;
                                result.point.y = point2D.y;
                            }
                        }
                    }
                }
            } else {
                List<Point2D> inouts = inout.get(building);
                for (Point2D point2D : inouts) {
                    if (result == null) {
                        result = new WayPoint(point2D.x, point2D.y, building, p.floor, "InOut");
                        d = Math.pow(p.point.x - point2D.x, 2) + Math.pow(p.point.y - point2D.y, 2);
                    } else {
                        double d1 = Math.pow(p.point.x - point2D.x, 2) + Math.pow(p.point.y - point2D.y, 2);
                        if (d1 < d) {
                            d = d1;
                            result.building = building;
                            result.point.x = point2D.x;
                            result.point.y = point2D.y;
                        }
                    }
                }
            }

            return result;
        }

        private WayPoint findNearByLift(WayPoint p, String building) {
            WayPoint result = null;
            double d = 0;
            if (TextUtils.isEmpty(building)) {
                for (String build : lift.keySet()) {
                    List<Point2D> lifts = lift.get(build);
                    if (lifts != null) {
                        for (Point2D point2D : lifts) {
                            if (result == null) {
                                result = new WayPoint(point2D.x, point2D.y, build, p.floor, "Lift");
                                d = Math.pow(p.point.x - point2D.x, 2) + Math.pow(p.point.y - point2D.y ,2);
                            }
                            else {
                                double d1 = Math.pow(p.point.x - point2D.x, 2) + Math.pow(p.point.y - point2D.y ,2);
                                if (d1 < d) {
                                    d = d1;
                                    result.building = build;
                                    result.point.x = point2D.x;
                                    result.point.y = point2D.y;
                                }
                            }
                        }
                    }
                }
            } else if (lift.containsKey(p.building)) {
                List<Point2D> lifts = lift.get(p.building);
                if (lifts != null) {
                    for (Point2D point2D : lifts) {
                        if (result == null) {
                            result = new WayPoint(point2D.x, point2D.y, p.building, p.floor, "Lift");
                            d = Math.pow(p.point.x - point2D.x, 2) + Math.pow(p.point.y - point2D.y ,2);
                        }
                        else {
                            double d1 = Math.pow(p.point.x - point2D.x, 2) + Math.pow(p.point.y - point2D.y ,2);
                            if (d1 < d) {
                                d = d1;
                                result.building = p.building;
                                result.point.x = point2D.x;
                                result.point.y = point2D.y;
                            }
                        }
                    }
                }
            }

            return result;
        }
    }

    private static class MyFindPathEventListener extends FindPathService.FindPathEventListener {
        private FindPathResult pathResult;

        public MyFindPathEventListener() {
            super();
        }

        public FindPathResult getReult() {
            return pathResult;
        }

        @Override
        public void onFindPathStatusChanged(Object sourceObject, EventStatus status) {
            if (sourceObject instanceof FindPathResult && status.equals(EventStatus.PROCESS_COMPLETE)) {
                pathResult = (FindPathResult) sourceObject;
            }
        }
    }

    public static class WayPoint {
        public Point2D point;
        public String building;
        public String floor;
        public String catalog;

        public WayPoint(double lng, double lat, String building, String floor) {
            point = new Point2D(lng, lat);
            this.building = building;
            this.floor = floor;
            catalog = "WayPoint";
        }

        public WayPoint(double lng, double lat, String building, String floor, String catalog) {
            point = new Point2D(lng, lat);
            this.building = building;
            this.floor = floor;
            this.catalog = catalog;
        }
    }
}
