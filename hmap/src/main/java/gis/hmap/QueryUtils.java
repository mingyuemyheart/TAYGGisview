package gis.hmap;

import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import com.supermap.android.commons.EventStatus;
import com.supermap.android.data.GetFeaturesByGeometryService;
import com.supermap.android.data.GetFeaturesBySQLParameters;
import com.supermap.android.data.GetFeaturesBySQLService;
import com.supermap.android.data.GetFeaturesResult;
import com.supermap.android.maps.Point2D;
import com.supermap.services.components.commontypes.Feature;
import com.supermap.services.components.commontypes.Geometry;
import com.supermap.services.components.commontypes.QueryParameter;
import com.supermap.services.components.commontypes.Rectangle2D;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * Created by Ryan on 2018/10/16.
 */

 class QueryUtils {

    public static class BuildingResult {
        public Feature feature;
        public List<Point2D> buildingGeometry;

        public BuildingResult(Feature feature, List<Point2D> buildingGeometry) {
            this.feature = feature;
            this.buildingGeometry = buildingGeometry;
        }
    }

    /**
     * 查询building信息
     * @param name
     * @param handler
     */
    public static void queryAllBuildings(String name, Handler handler) {
        new Thread(new QueryAllBuildingsRunnable(name, handler)).start();
    }

    private static class QueryAllBuildingsRunnable implements Runnable {
        String name;
        Handler handler;

        public QueryAllBuildingsRunnable(String name, Handler handler) {
            this.name = name;
            this.handler = handler;
        }

        @Override
        public void run() {
            GetFeaturesBySQLParameters sqlParameters = new GetFeaturesBySQLParameters();
            sqlParameters.datasetNames = new String[] { Common.parkId() + ":buildings" };
            sqlParameters.toIndex = 9999;
            QueryParameter queryParameter = new QueryParameter();
            queryParameter.name = name;
            sqlParameters.queryParameter = queryParameter;

            GetFeaturesBySQLService sqlService = new GetFeaturesBySQLService(Common.getHost() + Common.DATA_URL());
            MyGetFeaturesEventListener listener = new MyGetFeaturesEventListener();
            sqlService.process(sqlParameters, listener);
            try {
                listener.waitUntilProcessed();
            } catch (Exception e) {
                e.printStackTrace();
            }

            List<BuildingResult> buildings = null;
            GetFeaturesResult building = listener.getReult();
            if (building != null && building.features != null) {
                buildings = new ArrayList<>();
                for (Feature feature : building.features) {
                    Geometry geometry = feature.geometry;
                    List<Point2D> geoPoints = getPiontsFromGeometry(geometry);
                    BuildingResult br = new BuildingResult(feature, geoPoints);
                    buildings.add(br);
                }
            }

            if (buildings != null && buildings.size() > 0) {
                Message msg = new Message();
                msg.obj = buildings;
                msg.what = Common.QUERY_BUILDINGS;
                handler.sendMessage(msg);
            }
        }
    }

    /**
     * 查询室内地图
     * @param mapId
     * @param buildingId
     * @param florid
     * @param handler
     */
    public static void queryIndoorMap(String mapId, String buildingId, String florid, Handler handler) {
        new Thread(new QueryIndoorMapRunnable(mapId, buildingId, florid, handler)).start();
    }

    private static class QueryIndoorMapRunnable implements Runnable {
        String mapId;
        String buildingId;
        String florid;
        Handler handler;

        public QueryIndoorMapRunnable(String mapId, String buildingId, String florid, Handler handler) {
            this.mapId = mapId;
            this.buildingId = buildingId;
            this.florid = florid;
            this.handler = handler;
        }

        @Override
        public void run() {
            GetFeaturesBySQLParameters sqlParameters = new GetFeaturesBySQLParameters();
            sqlParameters.datasetNames = new String[] { mapId };
            sqlParameters.toIndex = 9999;
            QueryParameter queryParameter = new QueryParameter();
            queryParameter.attributeFilter = "BuildingId = \"" + buildingId + "\"";
            sqlParameters.queryParameter = queryParameter;

            GetFeaturesBySQLService sqlService = new GetFeaturesBySQLService(Common.getHost() + Common.DATA_URL());
            MyGetFeaturesEventListener listener = new MyGetFeaturesEventListener();
            sqlService.process(sqlParameters, listener);
            try {
                listener.waitUntilProcessed();
            } catch (Exception e) {
                e.printStackTrace();
            }

            List<List<Point2D>> buildingGeometry = null;
            Rectangle2D buildingBounds = null;
            GetFeaturesResult building = listener.getReult();
            if (building != null && building.features != null) {
                buildingGeometry = new ArrayList<>();
                for (Feature feature : building.features) {
                    Geometry geometry = feature.geometry;
                    List<Point2D> geoPoints = getPiontsFromGeometry(geometry);
                    if (geometry.parts.length > 1) {
                        int num = 0;
                        for (int j = 0; j < geometry.parts.length; j++) {
                            int count = geometry.parts[j];
                            List<Point2D> partList = geoPoints.subList(num, num + count);
                            buildingGeometry.add(partList);
                            num = num + count;
                        }
                    } else {
                        buildingGeometry.add(geoPoints);
                    }
                    if (buildingBounds == null)
                        buildingBounds = geometry.getBounds();
                    else {
                        if (buildingBounds.getLeft() > geometry.getBounds().getLeft())
                            buildingBounds.setLeft(geometry.getBounds().getLeft());
                        if (buildingBounds.getRight() < geometry.getBounds().getRight())
                            buildingBounds.setRight(geometry.getBounds().getRight());
                        if (buildingBounds.getTop() < geometry.getBounds().getTop())
                            buildingBounds.setTop(geometry.getBounds().getTop());
                        if (buildingBounds.getBottom() > geometry.getBounds().getBottom())
                            buildingBounds.setBottom(geometry.getBounds().getBottom());
                    }
                }
            }
            sqlParameters.datasetNames = new String[] { Common.parkId()+":"+florid };
            sqlParameters.queryParameter.attributeFilter = "BuildingId = \"" + buildingId + "\"";
            sqlService.process(sqlParameters, listener);
            try {
                listener.waitUntilProcessed();
            } catch (Exception e) {
                e.printStackTrace();
            }

            List<ModelData> rooms = null;
            GetFeaturesResult floor = listener.getReult();
            if (floor != null && floor.features != null) {
                rooms = new ArrayList<>();
                for (Feature feature : floor.features) {
                    for (int i = 0; i < feature.fieldValues.length; i++) {
                        Log.e("QueryIndoorMapRunnable", feature.fieldNames[i]+"---"+feature.fieldValues[i]);
                    }
                    Geometry geometry = feature.geometry;
                    List<List<Point2D>> roomPoints = new ArrayList<>();
                    List<Point2D> geoPoints = getPiontsFromGeometry(geometry);
                    if (geometry.parts.length > 1) {
                        int num = 0;
                        for (int j = 0; j < geometry.parts.length; j++) {
                            int count = geometry.parts[j];
                            List<Point2D> partList = geoPoints.subList(num, num + count);
                            roomPoints.add(partList);
                            num = num + count;
                        }
                    } else {
                        roomPoints.add(geoPoints);
                    }
                    HashMap<String, String> info = new HashMap<>();
                    for (int i=0; i<feature.fieldNames.length; i++)
                        info.put(feature.fieldNames[i], feature.fieldValues[i]);
                    info.put("FLOORID", florid);
                    String key = String.format("%s.%s.%s", buildingId, florid, info.get("SMID"));
                    rooms.add(new ModelData(key, null, roomPoints, info));
                }
            }

            sqlParameters.datasetNames = new String[] { Common.parkId()+":"+florid+"_LINE" };
            sqlService.process(sqlParameters, listener);
            try {
                listener.waitUntilProcessed();
            } catch (Exception e) {
                e.printStackTrace();
            }

            floor = listener.getReult();
            if (floor != null && floor.features != null) {
                if (rooms == null) rooms = new ArrayList<>();
                for (Feature feature : floor.features) {
                    Geometry geometry = feature.geometry;
                    List<List<Point2D>> linePoints = new ArrayList<>();
                    List<Point2D> geoPoints = getPiontsFromGeometry(geometry);
                    if (geometry.parts.length > 1) {
                        int num = 0;
                        for (int j = 0; j < geometry.parts.length; j++) {
                            int count = geometry.parts[j];
                            List<Point2D> partList = geoPoints.subList(num, num + count);
                            linePoints.add(partList);
                            num = num + count;
                        }
                    } else {
                        linePoints.add(geoPoints);
                    }
                    HashMap<String, String> info = new HashMap<>();
                    for (int i=0; i<feature.fieldNames.length; i++)
                        info.put(feature.fieldNames[i], feature.fieldValues[i]);
                    info.put("FLOORID", florid);
                    String key = String.format("%s.%s.%s", buildingId, florid, info.get("SMID"));
                    ModelData modelData = null;
                    for (ModelData model : rooms) {
                        if (model.key.equalsIgnoreCase(key)) {
                            modelData = model;
                            break;
                        }
                    }
                    if (modelData != null)
                        modelData.outline = linePoints;
                    else
                        rooms.add(new ModelData(key, linePoints, null, info));
                }
            }

            if (buildingGeometry != null || rooms != null) {
                IndoorMapData data = new IndoorMapData(buildingId, florid, buildingGeometry, buildingBounds, rooms);
                Message msg = new Message();
                msg.obj = data;
                msg.what = Common.QUERY_INDOOR_MAP;
                handler.sendMessage(msg);
            }
        }

    }

    public static class BasementMapResult implements Serializable {
        public String floorId;
        public List<List<Point2D>> structureGeometry;
        public List<List<Point2D>> floorGeometry;
        public Rectangle2D structureBounds;

        public BasementMapResult(String floorId, List<List<Point2D>> structureGeometry, Rectangle2D structureBounds, List<List<Point2D>> floorGeometry) {
            this.floorId = floorId;
            this.structureGeometry = structureGeometry;
            this.floorGeometry = floorGeometry;
            this.structureBounds = structureBounds;
        }
    }

    /**
     * 查询地下室停车场
     * @param mapId
     * @param buildingId
     * @param florid
     * @param handler
     */
    public static void queryBasementMap(String mapId, String buildingId, String florid, Handler handler) {
        new Thread(new QueryBasementMapRunnable(mapId, buildingId, florid, handler)).start();
    }

    private static class QueryBasementMapRunnable implements Runnable {
        String mapId;
        String buildingId;
        String florid;
        Handler handler;

        public QueryBasementMapRunnable(String mapId, String buildingId, String florid, Handler handler) {
            this.mapId = mapId;
            this.buildingId = buildingId;
            this.florid = florid;
            this.handler = handler;
        }

        @Override
        public void run() {
            GetFeaturesBySQLParameters sqlParameters = new GetFeaturesBySQLParameters();
            sqlParameters.datasetNames = new String[] {String.format("%s:%s_parking", Common.parkId(), florid)};
            sqlParameters.toIndex = 99999;
            QueryParameter queryParameter = new QueryParameter();
            queryParameter.attributeFilter = "BuildingId = \"" + buildingId + "\"";
            sqlParameters.queryParameter = queryParameter;

            Log.e("QueryBasementMap", Common.getHost() + Common.DATA_URL());
            GetFeaturesBySQLService sqlService = new GetFeaturesBySQLService(Common.getHost() + Common.DATA_URL());
            MyGetFeaturesEventListener listener = new MyGetFeaturesEventListener();
            sqlService.process(sqlParameters, listener);
            try {
                listener.waitUntilProcessed();
            } catch (Exception e) {
                e.printStackTrace();
            }

            List<List<Point2D>> structureGeometry = null;
            Rectangle2D structureBounds = null;
            GetFeaturesResult structure = listener.getReult();
            if (structure != null && structure.features != null) {
                structureGeometry = new ArrayList<>();
                for (Feature feature : structure.features) {
                    Geometry geometry = feature.geometry;
                    List<Point2D> geoPoints = getPiontsFromGeometry(geometry);
                    if (geometry.parts.length > 1) {
                        int num = 0;
                        for (int j = 0; j < geometry.parts.length; j++) {
                            int count = geometry.parts[j];
                            List<Point2D> partList = geoPoints.subList(num, num + count);
                            structureGeometry.add(partList);
                            num = num + count;
                        }
                    } else {
                        structureGeometry.add(geoPoints);
                    }
                    if (structureBounds == null)
                        structureBounds = geometry.getBounds();
                    else {
                        if (structureBounds.getLeft() > geometry.getBounds().getLeft())
                            structureBounds.setLeft(geometry.getBounds().getLeft());
                        if (structureBounds.getRight() < geometry.getBounds().getRight())
                            structureBounds.setRight(geometry.getBounds().getRight());
                        if (structureBounds.getTop() < geometry.getBounds().getTop())
                            structureBounds.setTop(geometry.getBounds().getTop());
                        if (structureBounds.getBottom() > geometry.getBounds().getBottom())
                            structureBounds.setBottom(geometry.getBounds().getBottom());
                    }
                }
            }

            sqlParameters.datasetNames = new String[] {String.format("%s:%s_LINE", Common.parkId(), florid)};
            sqlService.process(sqlParameters, listener);
            try {
                listener.waitUntilProcessed();
            } catch (Exception e) {
                e.printStackTrace();
            }

            List<List<Point2D>> floorGeometry = null;
            GetFeaturesResult floor = listener.getReult();
            if (floor != null && floor.features != null) {
                floorGeometry = new ArrayList<>();
                for (Feature feature : floor.features) {
                    Geometry geometry = feature.geometry;
                    List<Point2D> geoPoints = getPiontsFromGeometry(geometry);
                    if (geometry.parts.length > 1) {
                        int num = 0;
                        for (int j = 0; j < geometry.parts.length; j++) {
                            int count = geometry.parts[j];
                            List<Point2D> partList = geoPoints.subList(num, num + count);
                            floorGeometry.add(partList);
                            num = num + count;
                        }
                    } else {
                        floorGeometry.add(geoPoints);
                    }
                    if (structureBounds == null)
                        structureBounds = geometry.getBounds();
                    else {
                        if (structureBounds.getLeft() > geometry.getBounds().getLeft())
                            structureBounds.setLeft(geometry.getBounds().getLeft());
                        if (structureBounds.getRight() < geometry.getBounds().getRight())
                            structureBounds.setRight(geometry.getBounds().getRight());
                        if (structureBounds.getTop() < geometry.getBounds().getTop())
                            structureBounds.setTop(geometry.getBounds().getTop());
                        if (structureBounds.getBottom() > geometry.getBounds().getBottom())
                            structureBounds.setBottom(geometry.getBounds().getBottom());
                    }
                }
            }

            if (structureGeometry != null || floorGeometry != null) {
                BasementMapResult data = new BasementMapResult(florid, structureGeometry, structureBounds, floorGeometry);
                Message msg = new Message();
                msg.obj = data;
                msg.what = Common.QUERY_BASEMENT_MAP;
                handler.sendMessage(msg);
            }
        }

    }

    public static class PerimeterResult {
        public String parkId;
        public PerimeterStyle alarm;
        public PerimeterStyle normal;
        public List<List<Point2D>> alarmGeometry;
        public List<List<Point2D>> normalGeometry;

        public PerimeterResult(String parkId, PerimeterStyle alarm, PerimeterStyle normal,
                               List<List<Point2D>> alarmGeometry, List<List<Point2D>> normalGeometry) {
            this.parkId = parkId;
            this.alarm = alarm;
            this.normal = normal;
            this.alarmGeometry = alarmGeometry;
            this.normalGeometry = normalGeometry;
        }
    }

    public static void queryPerimeter(String parkId, PerimeterStyle alarm, PerimeterStyle normal, int[] alarmList, Handler handler) {
        new Thread(new QueryPerimeterRunnable(parkId, alarm, normal, alarmList, handler)).start();
    }

    private static class QueryPerimeterRunnable implements Runnable {
        private String parkId;
        private PerimeterStyle alarm;
        private PerimeterStyle normal;
        private int[] alarmList;
        private Handler handler;

        public QueryPerimeterRunnable(String parkId, PerimeterStyle alarm, PerimeterStyle normal, int[] alarmList, Handler handler) {
            this.parkId = parkId;
            this.alarm = alarm;
            this.normal = normal;
            this.alarmList = alarmList;
            this.handler = handler;
        }

        @Override
        public void run() {
            GetFeaturesBySQLParameters sqlParameters = new GetFeaturesBySQLParameters();
            sqlParameters.datasetNames = new String[] { Common.parkId()+":PMTR" };
            sqlParameters.toIndex = 9999;
            QueryParameter queryParameter = new QueryParameter();
            queryParameter.name = "PMTR@" + Common.parkId();
            sqlParameters.queryParameter = queryParameter;

            GetFeaturesBySQLService sqlService = new GetFeaturesBySQLService(Common.getHost() + Common.DATA_URL());
            MyGetFeaturesEventListener listener = new MyGetFeaturesEventListener();
            sqlService.process(sqlParameters, listener);
            try {
                listener.waitUntilProcessed();
            } catch (Exception e) {
                e.printStackTrace();
            }

            List<List<Point2D>> alarmGeometry = null;
            List<List<Point2D>> normalGeometry = null;
            GetFeaturesResult perimeter = listener.getReult();
            if (perimeter != null && perimeter.features != null) {
                alarmGeometry = new ArrayList<>();
                normalGeometry = new ArrayList<>();
                for (Feature feature : perimeter.features) {
                    boolean isAlarm = false;
                    for (int def : alarmList) {
                        if (feature.getID() == def) {
                            isAlarm = true;
                            break;
                        }
                    }
                    Geometry geometry = feature.geometry;
                    List<Point2D> geoPoints = getPiontsFromGeometry(geometry);
                    if (geometry.parts.length > 1) {
                        int num = 0;
                        for (int j = 0; j < geometry.parts.length; j++) {
                            int count = geometry.parts[j];
                            List<Point2D> partList = geoPoints.subList(num, num + count);
                            if (isAlarm)
                                alarmGeometry.add(partList);
                            else
                                normalGeometry.add(partList);
                            num = num + count;
                        }
                    } else {
                        if (isAlarm)
                            alarmGeometry.add(geoPoints);
                        else
                            normalGeometry.add(geoPoints);
                    }
                }
            }

            if (alarmGeometry != null || normalGeometry != null) {
                PerimeterResult data = new PerimeterResult(parkId, alarm, normal, alarmGeometry, normalGeometry);
                Message msg = new Message();
                msg.obj = data;
                msg.what = Common.QUERY_PERIMETER;
                handler.sendMessage(msg);
            }
        }
    }

    public static class ModelResult {
        public List<List<Point2D>> highlightGeometry;
        public List<PresentationStyle> highlightStyle;
        public List<List<Point2D>> normalGeometry;
        public PresentationStyle normalStyle;

        public ModelResult(List<List<Point2D>> highlightGeometry, List<PresentationStyle> highlightStyle, List<List<Point2D>> normalGeometry, PresentationStyle normalStyle) {
            this.highlightGeometry = highlightGeometry;
            this.highlightStyle = highlightStyle;
            this.normalGeometry = normalGeometry;
            this.normalStyle = normalStyle;
        }
    }

    /**
     * 查询模型高亮（车位）
     * @param modIds
     * @param buildingId
     * @param floorid
     * @param pss
     * @param normal
     * @param handler
     */
    public static void queryModel(List<String[]> modIds, String buildingId, String floorid, List<PresentationStyle> pss, PresentationStyle normal, Handler handler) {
        new Thread(new QueryModelRunnable(modIds, buildingId, floorid, pss, normal, handler)).start();
    }

    private static class QueryModelRunnable implements Runnable {
        private List<String[]> modIds;
        private String buildingId;
        private String floorid;
        private List<PresentationStyle> pss;
        private PresentationStyle normal;
        private Handler handler;

        public QueryModelRunnable(List<String[]> modIds, String buildingId, String floorid, List<PresentationStyle> pss, PresentationStyle normal, Handler handler) {
            this.modIds = modIds;
            this.buildingId = buildingId;
            this.floorid = floorid;
            this.pss = pss;
            this.normal = normal;
            this.handler = handler;
        }

        @Override
        public void run() {
            GetFeaturesBySQLParameters sqlParameters = new GetFeaturesBySQLParameters();
            sqlParameters.datasetNames = new String[] {String.format("%s:%s_parking", Common.parkId(), floorid)};
            sqlParameters.toIndex = 9999;
            QueryParameter queryParameter = new QueryParameter();
            queryParameter.attributeFilter = "BuildingId = \"" + buildingId + "\"";
            sqlParameters.queryParameter = queryParameter;

            Log.e("QueryBasementMap", Common.getHost()+Common.DATA_URL());
            GetFeaturesBySQLService sqlService = new GetFeaturesBySQLService(Common.getHost()+Common.DATA_URL());
            MyGetFeaturesEventListener listener = new MyGetFeaturesEventListener();
            sqlService.process(sqlParameters, listener);
            try {
                listener.waitUntilProcessed();
            } catch (Exception e) {
                e.printStackTrace();
            }

            List<List<Point2D>> highlightGeometry = null;
            List<PresentationStyle> highlightStyle = null;
            List<List<Point2D>> normalGeometry = null;
            GetFeaturesResult model = listener.getReult();
            if (model != null && model.features != null) {
                highlightGeometry = new ArrayList<>();
                highlightStyle = new ArrayList<>();
                normalGeometry = new ArrayList<>();
                for (Feature feature : model.features) {
                    Map<String, String> dataMap = new LinkedHashMap<>();
                    for (int i = 0; i < feature.fieldNames.length; i++) {
                        dataMap.put(feature.fieldNames[i], feature.fieldValues[i]);
                    }
                    String parkingId = dataMap.get("PID");
                    Log.e("parkingId", parkingId);
                    boolean isHighLight = false;
                    int index = 0;
                    if (modIds != null)
                    for (String[] ids : modIds) {
                        for (String highlights : ids) {
                            if (TextUtils.equals(parkingId, highlights)) {
                                isHighLight = true;
                                break;
                            }
                        }
                        if (isHighLight)
                            break;
                        index++;
                    }
                    Geometry geometry = feature.geometry;
                    List<Point2D> geoPoints = getPiontsFromGeometry(geometry);
                    if (geometry.parts.length > 1) {
                        int num = 0;
                        for (int j = 0; j < geometry.parts.length; j++) {
                            int count = geometry.parts[j];
                            List<Point2D> partList = geoPoints.subList(num, num + count);
                            if (isHighLight) {
                                highlightGeometry.add(partList);
                                if (pss != null && pss.size() > index)
                                    highlightStyle.add(pss.get(index));
                                else
                                    highlightStyle.add(normal);
                            }
                            else
                                normalGeometry.add(partList);
                            num = num + count;
                        }
                    } else {
                        if (isHighLight) {
                            highlightGeometry.add(geoPoints);
                            if (pss != null && pss.size() > index)
                                highlightStyle.add(pss.get(index));
                            else
                                highlightStyle.add(normal);
                        }
                        else
                            normalGeometry.add(geoPoints);
                    }
                }
            }

            if (highlightGeometry != null || normalGeometry != null) {
                ModelResult data = new ModelResult(highlightGeometry, highlightStyle, normalGeometry, normal);
                Message msg = new Message();
                msg.obj = data;
                msg.what = Common.QUERY_MODEL;
                handler.sendMessage(msg);
            }
        }
    }

    private static class MyGetFeaturesEventListener extends GetFeaturesByGeometryService.GetFeaturesEventListener {
        private GetFeaturesResult lastResult;

        public MyGetFeaturesEventListener() {
            super();
            // TODO Auto-generated constructor stub
        }

        public GetFeaturesResult getReult() {
            return lastResult;
        }

        @Override
        public void onGetFeaturesStatusChanged(Object sourceObject, EventStatus status) {
            if (sourceObject instanceof GetFeaturesResult && status.equals(EventStatus.PROCESS_COMPLETE)) {
                lastResult = (GetFeaturesResult) sourceObject;
            }
        }
    }

    public static List<Point2D> getPiontsFromGeometry(Geometry geometry) {
        List<Point2D> geoPoints = new ArrayList<Point2D>();
        com.supermap.services.components.commontypes.Point2D[] points = geometry.points;
        for (com.supermap.services.components.commontypes.Point2D point : points) {
            Point2D geoPoint = new Point2D(point.x, point.y);
            geoPoints.add(geoPoint);
        }
        return geoPoints;
    }

    public static ObjectInfo getBuildingInfo(String parkId, String buildingId) {
        GetFeaturesBySQLParameters sqlParameters = new GetFeaturesBySQLParameters();
        sqlParameters.datasetNames = new String[] { parkId+":Buildings" };
        sqlParameters.toIndex = 9999;
        QueryParameter queryParameter = new QueryParameter();
        queryParameter.attributeFilter = "BuildingId = \"" + buildingId + "\"";
        sqlParameters.queryParameter = queryParameter;

        GetFeaturesBySQLService sqlService = new GetFeaturesBySQLService(Common.getHost() + Common.DATA_URL());
        MyGetFeaturesEventListener listener = new MyGetFeaturesEventListener();
        sqlService.process(sqlParameters, listener);
        try {
            listener.waitUntilProcessed();
        } catch (Exception e) {
            e.printStackTrace();
        }

        GetFeaturesResult building = listener.getReult();
        if (building != null && building.featureCount > 0) {
            Feature feature = building.features[0];
            return new ObjectInfo(feature.fieldNames, feature.fieldValues);
        }

        return null;
    }

    public static ObjectInfo getObjectInfo(String parkId, double lat, double lng) {
        GetFeaturesBySQLParameters sqlParameters = new GetFeaturesBySQLParameters();
        sqlParameters.datasetNames = new String[] { parkId+":Buildings" };
        sqlParameters.toIndex = 9999;
        QueryParameter queryParameter = new QueryParameter();
        queryParameter.attributeFilter = String.format("SMSDRIW <= %f AND SMSDRIE >= %f AND SMSDRIN >= %f AND SMSDRIS <= %f", lng, lng, lat, lat);
        sqlParameters.queryParameter = queryParameter;

        GetFeaturesBySQLService sqlService = new GetFeaturesBySQLService(Common.getHost() + Common.DATA_URL());
        MyGetFeaturesEventListener listener = new MyGetFeaturesEventListener();
        sqlService.process(sqlParameters, listener);

        try {
            listener.waitUntilProcessed();
        } catch (Exception e) {
            e.printStackTrace();
        }

        GetFeaturesResult building = listener.getReult();
        if (building != null && building.featureCount > 0) {
            Feature feature = building.features[0];
            return new ObjectInfo(feature.fieldNames, feature.fieldValues);
        }

        return null;
    }

    public static List<ObjectInfo> getObjects(String table, String filter) {
        GetFeaturesBySQLParameters sqlParameters = new GetFeaturesBySQLParameters();
        String [] ds = table.split(",");
        String [] datasets = new String[ds.length];
        for(int i=0; i < ds.length; i ++){
            datasets[i] = Common.parkId()+":"+ds[i];
        }
        sqlParameters.datasetNames = datasets;
        sqlParameters.toIndex = 999999;
        QueryParameter queryParameter = new QueryParameter();
        queryParameter.attributeFilter = filter;
        sqlParameters.queryParameter = queryParameter;

        GetFeaturesBySQLService sqlService = new GetFeaturesBySQLService(Common.getHost() + Common.DATA_URL());
        MyGetFeaturesEventListener listener = new MyGetFeaturesEventListener();
        sqlService.process(sqlParameters, listener);

        try {
            listener.waitUntilProcessed();
        } catch (Exception e) {
            e.printStackTrace();
        }

        GetFeaturesResult result = listener.getReult();
        if (result != null && result.featureCount > 0) {
            List<ObjectInfo> ret = new ArrayList<>();
            for (Feature feature : result.features)
                ret.add(new ObjectInfo(feature.fieldNames, feature.fieldValues));
            return ret;
        }

        return null;
    }

    public static List<BuildingResult> getBuildings() {
        GetFeaturesBySQLParameters sqlParameters = new GetFeaturesBySQLParameters();
        sqlParameters.datasetNames = new String[] { Common.parkId() + ":buildings" };
        sqlParameters.toIndex = 9999;
        QueryParameter queryParameter = new QueryParameter();
        queryParameter.name = "buildings@" + Common.parkId();
        sqlParameters.queryParameter = queryParameter;

        GetFeaturesBySQLService sqlService = new GetFeaturesBySQLService(Common.getHost() + Common.DATA_URL());
        MyGetFeaturesEventListener listener = new MyGetFeaturesEventListener();
        sqlService.process(sqlParameters, listener);
        try {
            listener.waitUntilProcessed();
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<BuildingResult> buildings = null;
        GetFeaturesResult building = listener.getReult();
        if (building != null && building.features != null) {
            buildings = new ArrayList<>();
            for (Feature feature : building.features) {
                Geometry geometry = feature.geometry;
                List<Point2D> geoPoints = getPiontsFromGeometry(geometry);
                BuildingResult br = new BuildingResult(feature, geoPoints);
                buildings.add(br);
            }
        }

        return buildings;
    }

    public static List<Feature> queryFloors(String buildingId, String floorId) {
        GetFeaturesBySQLParameters sqlParameters = new GetFeaturesBySQLParameters();
        sqlParameters.datasetNames = new String[] { Common.parkId()+":"+floorId };
        sqlParameters.toIndex = 99999;
        QueryParameter queryParameter = new QueryParameter();
        queryParameter.attributeFilter = "BuildingId = \"" + buildingId + "\"";
        sqlParameters.queryParameter = queryParameter;

        GetFeaturesBySQLService sqlService = new GetFeaturesBySQLService(Common.getHost() + Common.DATA_URL());
        MyGetFeaturesEventListener listener = new MyGetFeaturesEventListener();
        sqlService.process(sqlParameters, listener);
        try {
            listener.waitUntilProcessed();
        } catch (Exception e) {
            e.printStackTrace();
        }

        GetFeaturesResult floor = listener.getReult();
        List<Feature> result = new ArrayList<>();
        if (floor != null && floor.features != null) {
            for (int i=0; i<floor.featureCount; i++)
                result.add(floor.features[i]);
        }

        return result;
    }

    public static List<Feature> queryBasement(String floorId) {
        GetFeaturesBySQLParameters sqlParameters = new GetFeaturesBySQLParameters();
        sqlParameters.datasetNames = new String[] { Common.parkId()+":"+floorId };
        sqlParameters.toIndex = 99999;
        QueryParameter queryParameter = new QueryParameter();
        sqlParameters.queryParameter = queryParameter;

        GetFeaturesBySQLService sqlService = new GetFeaturesBySQLService(Common.getHost() + Common.DATA_URL());
        MyGetFeaturesEventListener listener = new MyGetFeaturesEventListener();
        sqlService.process(sqlParameters, listener);
        try {
            listener.waitUntilProcessed();
        } catch (Exception e) {
            e.printStackTrace();
        }

        GetFeaturesResult structure = listener.getReult();
        List<Feature> result = new ArrayList<>();
        if (structure != null && structure.features != null) {
            for (int i=0; i<structure.featureCount; i++)
                result.add(structure.features[i]);
        }

        return result;
    }

    /**
     * 获取园区id
     * @param lat
     * @param lng
     * @return
     */
    public static String[] queryPark(double lat, double lng) {
        GetFeaturesBySQLParameters sqlParameters = new GetFeaturesBySQLParameters();
        Log.e("queryPark", Common.globalParkId()+":"+Common.globalDataset());
        sqlParameters.datasetNames = new String[] { Common.globalParkId()+":"+Common.globalDataset() };
        sqlParameters.toIndex = 99999;
        QueryParameter queryParameter = new QueryParameter();
        sqlParameters.queryParameter = queryParameter;

        Log.e("queryPark", Common.getHost() + Common.GLOBALDATA_URL());
        GetFeaturesBySQLService sqlService = new GetFeaturesBySQLService(Common.getHost() + Common.GLOBALDATA_URL());
        MyGetFeaturesEventListener listener = new MyGetFeaturesEventListener();
        sqlService.process(sqlParameters, listener);
        try {
            listener.waitUntilProcessed();
        } catch (Exception e) {
            e.printStackTrace();
        }

        GetFeaturesResult parks = listener.getReult();
        if (parks != null && parks.features != null) {
            for (int i=0; i<parks.featureCount; i++) {
                Feature park = parks.features[i];
                double west=0, north=0, east=0, sourth=0;
                for (int t = 0; t < park.fieldNames.length; t++) {
                    if (park.fieldNames[t].equalsIgnoreCase("SMSDRIW"))
                        west = Double.parseDouble(park.fieldValues[t]);
                    else if (park.fieldNames[t].equalsIgnoreCase("SMSDRIN"))
                        north = Double.parseDouble(park.fieldValues[t]);
                    else if (park.fieldNames[t].equalsIgnoreCase("SMSDRIE"))
                        east = Double.parseDouble(park.fieldValues[t]);
                    else if (park.fieldNames[t].equalsIgnoreCase("SMSDRIS"))
                        sourth = Double.parseDouble(park.fieldValues[t]);
                }
                if (lng <= east && lng >= west && lat <= north && lat >= sourth) {
                    String[] result = new String[]{"", "", ""};
                    int filled = 0;
                    for (int j = 0; j < park.fieldNames.length; j++) {
                        if (park.fieldNames[j].equalsIgnoreCase(Common.parkIdName())) {
                            result[0] = park.fieldValues[j];
                            filled++;
                        } else if (park.fieldNames[j].equalsIgnoreCase(Common.parkCenterNameX())) {
                            result[1] = park.fieldValues[j];
                            filled++;
                        } else if (park.fieldNames[j].equalsIgnoreCase(Common.parkCenterNameY())) {
                            result[2] = park.fieldValues[j];
                            filled++;
                        }
                        if (filled >= 3)
                            break;
                    }
                    return result;
                }
            }
        }

        return null;
    }

    public static boolean isInPolygon(Point2D point, Point2D[] points) {
        int nCross = 0;
        for (int i = 0; i < points.length; i++) {
            Point2D p1 = points[i];
            Point2D p2 = points[(i + 1) % points.length];
            // 求解 y=p.y 与 p1 p2 的交点
            // p1p2 与 y=p0.y平行
            if (p1.y == p2.y)
                continue;
            // 交点在p1p2延长线上
            if (point.y < Math.min(p1.y, p2.y))
                continue;
            // 交点在p1p2延长线上
            if (point.y >= Math.max(p1.y, p2.y))
                continue;
            // 求交点的 X 坐标
            double x = (double) (point.y - p1.y) * (double) (p2.x - p1.x)
                    / (double) (p2.y - p1.y) + p1.x;
            // 只统计单边交点
            if (x > point.x)
                nCross++;
        }
        return (nCross % 2 == 1);
    }

    public static Feature[] queryDatasetAll(String target, boolean global) {
        GetFeaturesBySQLParameters sqlParameters = new GetFeaturesBySQLParameters();
        if (global)
            sqlParameters.datasetNames = new String[] { Common.globalParkId()+":"+target };
        else
            sqlParameters.datasetNames = new String[] { Common.parkId()+":"+target };
        sqlParameters.toIndex = 99999;
        QueryParameter queryParameter = new QueryParameter();
        sqlParameters.queryParameter = queryParameter;

        String dataUrl;
        if (global)
            dataUrl = Common.GLOBALDATA_URL();
        else
            dataUrl = Common.DATA_URL();
        GetFeaturesBySQLService sqlService = new GetFeaturesBySQLService(Common.getHost() + dataUrl);
        MyGetFeaturesEventListener listener = new MyGetFeaturesEventListener();
        sqlService.process(sqlParameters, listener);
        try {
            listener.waitUntilProcessed();
        } catch (Exception e) {
            e.printStackTrace();
        }

        GetFeaturesResult result = listener.getReult();
        if (result != null && result.features != null) {
            return result.features;
        }

        return null;
    }
}
