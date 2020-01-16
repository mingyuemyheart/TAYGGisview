package gis.hmap;


import android.content.Context;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;
import android.util.Log;

import com.supermap.android.data.GetFeaturesResult;
import com.supermap.android.maps.Point2D;
import com.supermap.services.components.commontypes.Feature;
import com.supermap.services.components.commontypes.Geometry;
import com.supermap.services.components.commontypes.Rectangle2D;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Ryan on 2019/6/2.
 */

final class GisDataCache {
    private static GisDataCache _instance = null;
    private Context context = null;
    private MapCacheListener mapCacheListener;
//    private Object initIndoorlock = new Object();
//    private Object readIndoorlock = new Object();

    private AtomicInteger indoorCacheCounter = new AtomicInteger();
    private AtomicInteger basementCacheCounter = new AtomicInteger();
    private boolean hasError = false;
    private HashMap<String, Feature> buildingCache = new HashMap<>();
    private List<String> basementList = new Vector<>();
    private static List<IVASMappingData> iVasMapping = new ArrayList<>();
    private List<BuildingConvertMappingData> buildingMapping = new ArrayList<>();

    private GisDataCache(Context context, MapCacheListener listener) {
        this.context = context;
        this.mapCacheListener = listener;
    }

    public static GisDataCache getInstance(Context context, MapCacheListener listener) {
        if (_instance == null)
            _instance = new GisDataCache(context, listener);

        return _instance;
    }

    public static void clearCaches() {
        if (_instance != null) {
            _instance.buildingCache.clear();
            _instance.basementList.clear();
            _instance.iVasMapping.clear();
            _instance.buildingMapping.clear();
        }
    }

    public static void initIVASMapping(final boolean test, final IVASMappingListener callback) {
        final boolean useTest = test;
        Common.fixedThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                Feature[] features = QueryUtils.queryDatasetAll("IVAS", true);
                if (features != null) {
                    for (Feature feature : features) {
                        IVASMappingData data = new IVASMappingData(feature, useTest);
                        iVasMapping.add(data);
                    }
                }
                if (callback != null) {
                    if (iVasMapping.size() > 0) {
                        callback.onIVASMappingSuccess(iVasMapping);
                    } else {
                        callback.onIVASMappingFailed("暂无查询数据");
                    }
                }
            }
        });
    }

    public IVASMappingData getIVASBuilding(String id) {
        for (IVASMappingData data : iVasMapping) {
            if (TextUtils.isEmpty(data.ivasBuildingId))
                continue;
            if (data.ivasBuildingId.equalsIgnoreCase(id))
                return data;
        }

        return null;
    }

    public void initBuildingConvert() {
        Feature[] features = QueryUtils.queryDatasetAll("buildConversion", false);
        if (features != null) {
            for (Feature feature : features) {
                BuildingConvertMappingData data = new BuildingConvertMappingData(feature);
                buildingMapping.add(data);
                Common.fixedThreadPool.execute(new InitBasementFloorMap(data.targetId, data.floorList));
            }
        }
    }

    public BuildingConvertMappingData getBuidingConver(String buildingId, String floorId) {
        BuildingConvertMappingData result = null;

        if (buildingMapping != null) {
            for (BuildingConvertMappingData data : buildingMapping) {
                if (data.buildingId.equalsIgnoreCase(buildingId)) {
                    boolean exist = false;
                    for (String floor : data.floorList) {
                        if (floor.equalsIgnoreCase(floorId)) {
                            exist = true;
                            break;
                        }
                    }
                    if (exist) {
                        result = data;
                        break;
                    }
                }
            }
        }

        return result;
    }

    public void initIndoorMaps(List<QueryUtils.BuildingResult> buildings) {
        hasError = false;
        if (buildings != null) {
            List<Feature> features = new ArrayList<>();
            for (QueryUtils.BuildingResult building : buildings) {
                String buildingId = getFeatureValue(building.feature, "BUILDINGID");
                if (!TextUtils.isEmpty(buildingId)) {
                    String idKey = String.format("%s.%s", Common.parkId(), buildingId);
                    buildingCache.put(idKey, building.feature);
                    features.add(building.feature);
                }
            }
            Log.i("--> GisCache", "Check and updating indoor map data.");
            indoorCacheCounter.set(buildings.size());
            for (QueryUtils.BuildingResult building : buildings) {
                Common.fixedThreadPool.execute(new InitFloorMap(building.feature));
            }
        }
        Common.fixedThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                initBuildingConvert();
            }
        });
    }

    public Feature getBuilding(String buildingId) {
        String idKey = String.format("%s.%s", Common.parkId(), buildingId);
        if (buildingCache.containsKey(idKey)) {
            return buildingCache.get(idKey);
        } else
            return null;
    }

    public Feature[] getFloor(String buildingId, String floorId) {
        String idKey = String.format("%s.%s.%s", Common.parkId(), buildingId, floorId);
        Feature[] floor = null;
        Feature[] floorLine = null;
        floor = read(idKey);
        floorLine = read(idKey + "_LINE");
        int cnt = 0;
        if (floor != null)
            cnt += floor.length;
        if (floorLine != null)
            cnt += floorLine.length;
        Feature[] result = new Feature[cnt];
        cnt = 0;
        if (floor != null)
            for (Feature feature : floor)
                result[cnt++] = feature;
        if (floorLine != null)
            for (Feature feature : floorLine)
                result[cnt++] = feature;

        return result;
    }

    public static String getFeatureValue(Feature feature, String fieldName) {
        String ret = "";
        for (int i=0; i<feature.fieldNames.length; i++) {
            if (feature.fieldNames[i].equalsIgnoreCase(fieldName)) {
                ret = feature.fieldValues[i];
                break;
            }
        }

        return ret;
    }

    private class InitFloorMap implements Runnable {
        private Feature building;

        public InitFloorMap(Feature building) {
            this.building = building;
        }

        @Override
        public void run() {
            String buildingId = getFeatureValue(building, "BUILDINGID");
            String idkey = String.format("%s.%s", Common.parkId(), buildingId);

            String floorlist = getFeatureValue(building, "FLOORLIST");
            if (!TextUtils.isEmpty(floorlist)) {
                File dir = _instance.context.getExternalFilesDir("Maps");
                String[] floors = floorlist.split(",");
                for (String floorId : floors) {
                    if (floorId.startsWith("B")) {
                        if (!basementList.contains(floorId))
                            basementList.add(floorId);
                        continue;
                    }
                    String keyStr = String.format("%s.%s", idkey, floorId);
                    File file = new File(dir, keyStr + ".tag");
                    if (file.exists())
                        Log.i("-->", "has " + keyStr);
                    else {
                        List<Feature> features = QueryUtils.queryFloors(buildingId, floorId);
                        if (features != null && features.size() > 0) {
                            write(keyStr + ".new", features);
                            File file1 = new File(dir, keyStr + ".new");
                            File file2 = new File(dir, keyStr);
                            if (file2.exists())
                                file2.delete();
                            file1.renameTo(file2);
                            mark(keyStr);
                        }
                    }
                    file = new File(dir, keyStr + "_LINE.tag");
                    if (file.exists())
                        Log.i("-->", "has " + keyStr + "_LINE");
                    else {
                        List<Feature> features = QueryUtils.queryFloors(buildingId, floorId + "_LINE");
                        write(String.format("%s.%s_LINE.new", idkey, floorId), features);
                        File file1 = new File(dir, keyStr + "_LINE.new");
                        File file2 = new File(dir, keyStr + "_LINE");
                        if (file2.exists())
                            file2.delete();
                        file1.renameTo(file2);
                        mark(keyStr + "_LINE");
                    }
                }
            }
            if (indoorCacheCounter.decrementAndGet() == 0) {
                if (basementList.size() > 0) {
                    String bases = "";
                    basementCacheCounter.set(basementList.size());
                    for (String base : basementList) {
                        bases += base + " ";
                        Common.fixedThreadPool.execute(new InitBasement(base));
                    }
                    Log.i("-->indoor", "done! Basements: " + bases);
                } else {
                    if (mapCacheListener != null)
                        mapCacheListener.cacheEvent(new MapCacheEvent(!hasError, "Indoor"));
                }
            }
        }
    }

    private class InitBasementFloorMap implements Runnable {
        private String buildingId;
        private String[] floors;

        public InitBasementFloorMap(String buildingId, String[] floorList) {
            this.buildingId = buildingId;
            this.floors = floorList;
        }

        @Override
        public void run() {
            String idkey = String.format("%s.%s", Common.parkId(), buildingId);
            File dir = _instance.context.getExternalFilesDir("Maps");
            for (String floorId : floors) {
                if (floorId.startsWith("F"))
                    continue;
                String keyStr = String.format("%s.%s", idkey, floorId);
                File file = new File(dir, keyStr + ".tag");
                if (file.exists())
                    Log.i("-->", "has " + keyStr);
                else {
                    List<Feature> features = QueryUtils.queryFloors(buildingId, floorId);
                    if (features != null && features.size() > 0) {
                        write(keyStr + ".new", features);
                        File file1 = new File(dir, keyStr + ".new");
                        File file2 = new File(dir, keyStr);
                        if (file2.exists())
                            file2.delete();
                        file1.renameTo(file2);
                        mark(keyStr);
                    }
                }
                file = new File(dir, keyStr + "_LINE.tag");
                if (file.exists())
                    Log.i("-->", "has " + keyStr + "_LINE");
                else {
                    List<Feature> features = QueryUtils.queryFloors(buildingId, floorId + "_LINE");
                    write(String.format("%s.%s_LINE.new", idkey, floorId), features);
                    File file1 = new File(dir, keyStr + "_LINE.new");
                    File file2 = new File(dir, keyStr + "_LINE");
                    if (file2.exists())
                        file2.delete();
                    file1.renameTo(file2);
                    mark(keyStr + "_LINE");
                }
            }
        }
    }

    private class InitBasement implements Runnable {
        private String floor;

        public InitBasement(String floor) {
            this.floor = floor;
        }

        @Override
        public void run() {
            String keyStr = String.format("%s.Basement.%s", Common.parkId(), floor);
            File dir = _instance.context.getExternalFilesDir("Maps");
            File file = new File(dir, keyStr + ".tag");
            if (file.exists())
                Log.i("-->", "has " + keyStr);
            else {
                List<Feature> features = QueryUtils.queryBasement(floor);
                if (features != null && features.size() > 0) {
                    write(keyStr + ".new", features);
                    File file1 = new File(dir, keyStr + ".new");
                    File file2 = new File(dir, keyStr);
                    if (file2.exists())
                        file2.delete();
                    file1.renameTo(file2);
                    mark(keyStr);
                }
            }
            file = new File(dir, keyStr + "_LINE.tag");
            if (file.exists())
                Log.i("-->", "has " + keyStr + "_LINE");
            else {
                List<Feature> features = QueryUtils.queryBasement(floor + "_LINE");
                if (features != null && features.size() > 0) {
                    write(keyStr + "_LINE.new", features);
                    File file1 = new File(dir, keyStr + "_LINE.new");
                    File file2 = new File(dir, keyStr + "_LINE");
                    if (file2.exists())
                        file2.delete();
                    file1.renameTo(file2);
                    mark(keyStr + "_LINE");
                }
            }

            if (basementCacheCounter.decrementAndGet() == 0) {
                if (mapCacheListener != null)
                    mapCacheListener.cacheEvent(new MapCacheEvent(!hasError, "Indoor+Basement"));
            }
        }
    }

    public static QueryUtils.BasementMapResult getBasement(String key) {
        QueryUtils.BasementMapResult result = null;
        long t1 = (new Date()).getTime();
        String idkey = String.format("%s.Basement.%s", Common.parkId(), key);
        File dir = _instance.context.getExternalFilesDir("Maps");
        File file = new File(dir, idkey);
        List<List<Point2D>> structureGeometry = null;
        List<List<Point2D>> floorGeometry = null;
        Rectangle2D structureBounds = null;
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buf = new byte[fis.available()];
                fis.read(buf);
                ByteArrayInputStream bis = new ByteArrayInputStream(buf);
                ObjectInputStream oin = new ObjectInputStream(bis);
                List<Feature> features = (List<Feature>) oin.readObject();
                oin.close();
                bis.close();
                if (features != null && features.size() > 0) {
                    structureGeometry = new ArrayList<>();
                    for (Feature feature : features) {
                        Geometry geometry = feature.geometry;
                        List<Point2D> geoPoints = QueryUtils.getPiontsFromGeometry(geometry);
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
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        file = new File(dir, idkey + "_LINE");
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buf = new byte[fis.available()];
                fis.read(buf);
                ByteArrayInputStream bis = new ByteArrayInputStream(buf);
                ObjectInputStream oin = new ObjectInputStream(bis);
                List<Feature> features = (List<Feature>) oin.readObject();
                oin.close();
                bis.close();
                if (features != null && features.size() > 0) {
                    floorGeometry = new ArrayList<>();
                    for (Feature feature : features) {
                        Geometry geometry = feature.geometry;
                        List<Point2D> geoPoints = QueryUtils.getPiontsFromGeometry(geometry);
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
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        if (structureGeometry != null || floorGeometry != null) {
            result = new QueryUtils.BasementMapResult(key, structureGeometry, structureBounds, floorGeometry);
        }
        long t2 = (new Date()).getTime();
        Log.i("-->", String.format("read %s: %d", key, t2-t1));

        return result;
    }

    public static void mark(String key) {
        File dir = _instance.context.getExternalFilesDir("Maps");
        File file = new File(dir, key + ".tag");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(0xffefabba);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void write(String key, List<Feature> floorsFeature) {
        long t1 = (new Date()).getTime();
        File dir = _instance.context.getExternalFilesDir("Maps");
        File file = new File(dir, key);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(floorsFeature);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(bos.toByteArray());
            fos.close();
            bos.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        long t2 = (new Date()).getTime();
        Log.i("-->", String.format("%s: %d", key, t2-t1));
    }

    public static Feature[] read(String key) {
        List<Feature> features = null;
        long t1 = (new Date()).getTime();

        File dir = _instance.context.getExternalFilesDir("Maps");
        File file = new File(dir, key);
        if (file.exists()) {

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buf = new byte[fis.available()];
                fis.read(buf);
                ByteArrayInputStream bis = new ByteArrayInputStream(buf);
                ObjectInputStream oin = new ObjectInputStream(bis);
                features = (List<Feature>) oin.readObject();
                oin.close();
                bis.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            } catch (ClassNotFoundException ex) {
                ex.printStackTrace();
            }
        }
        long t2 = (new Date()).getTime();
        Log.i("-->", String.format("read %s: %d", key, t2-t1));

        return features == null ? null : features.toArray(new Feature[0]);
    }

    public static String getFileMD5(File file) {
        if (!file.isFile()) {
            return null;
        }
        MessageDigest digest = null;
        FileInputStream in = null;
        byte buffer[] = new byte[1024];
        int len;
        try {
            digest = MessageDigest.getInstance("MD5");
            in = new FileInputStream(file);
            while ((len = in.read(buffer, 0, 1024)) != -1) {
                digest.update(buffer, 0, len);
            }
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        BigInteger bigInt = new BigInteger(1, digest.digest());
        return bigInt.toString(16);
    }
}
