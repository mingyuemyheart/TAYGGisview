package gis.hmap;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Criteria;
import android.location.Location;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.supermap.android.maps.AbstractTileLayerView;
import com.supermap.android.maps.BoundingBox;
import com.supermap.android.maps.DefaultItemizedOverlay;
import com.supermap.android.maps.DrawableOverlay;
import com.supermap.android.maps.LayerView;
import com.supermap.android.maps.LineOverlay;
import com.supermap.android.maps.MapController;
import com.supermap.android.maps.MapView;
import com.supermap.android.maps.Overlay;
import com.supermap.android.maps.Point2D;
import com.supermap.android.maps.PolygonOverlay;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class GisView extends RelativeLayout implements Overlay.OverlayTapListener, MapView.MapViewEventListener, AMapLocationListener {

    public static String TAG = "GisView-";

    private boolean logEnable = false;
    public void setLogEnable(boolean logEnable) {
        this.logEnable = logEnable;
    }

    private int indoorZIndex = -10;//室内地图
    private int parkingZIndex = -9;//室内停车场

    private static final String calculatdRouteKey = "[calculatdRoute]";
    private static final String indoorKeyTemplate = "indoor[building:%s]";
    private static final String basementKeyTemplate = "indoor[basement:%s]";
    private static final String indoorStyleKeyTemplate = "indoorStyle[%s:%s:%s]";
    private static final String perimeterKey = "[perimeter]";
    private static final String modelsKey = "[models]";
    private static final String buildingTouchKey = "[buildingTouch]";

    private GisView _instance = null;
    private MapView mapView;
    protected AbstractTileLayerView mapLayer = null;
    protected Map<String, List<Overlay>> namedOverlays;
    protected List<LineOverlay> routeOverlay;
    protected List<MarkerListener> mMarkerListener;
    protected List<BuildingListener> mBuildingListener;
    protected List<ModelListener> mModelListener;
    protected List<ZoomListener> mZoomListener;
    protected List<MapListener> mMapListener;
    protected IndoorCallback indoorCallback;
    protected static List<LocationListener> mPosListener;
    protected TouchOverlay touchOverlay;
    protected int mHideLevel = 1;
    protected List<QueryUtils.BuildingResult> buildings;
    protected Handler handler;
    protected List<HeatmapDrawable> heatmapList;
    protected HashMap<String, IndoorMapData> openIndoors;
    protected HashMap<String, QueryUtils.BasementMapResult> openBasements;
    protected List<String> openedMaps;
    protected String currIndoorDetect = "";
    protected NetWorkAnalystUtil.CalculatedRoute calculatedRoute;
    protected String currIndoorMap = "";
    protected HashMap<String, GeneralMarker> defaultFacilities;
    protected boolean isShowHighLight = false;
    protected int maxZoomLevel = 5;
    protected static HashMap<String, String> floorMapper;
    protected int lastZoomLevel = -1;
    protected int indoorZoomLevel = 0;//室内地图默认缩放值
    protected ZoomToIndoorListener mZoomToIndoorListener;//室内地图缩放事件
    protected String defaultZoomIndoor = "F01";//室内地图默认楼层
    protected CalculateRouteListener mCalculateRouteListener;//路径规划绘制
    protected PathPlanDataListener pathPlanDataListener;//获取路径规划数据
    protected static boolean autoClearCachedTiles = false;
    protected MapCacheListener mMapCacheListener;
    protected MapLoadedListener mMapLoadedListener;
    protected List<MapMoveListener> mMapMoveListener;
    protected boolean isMapLoaded = false;

    //声明AMapLocationClient类对象
    public AMapLocationClient mLocationClient = null;
    protected static boolean isLocatorRunning = false;
    private SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);

    public GisView(Context context) {
        super(context);
        init(context,null, 0,0);
    }

    public GisView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0,0);
    }

    public GisView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs, defStyle,0);
    }

    public GisView(Context context, AttributeSet attrs, int defStyle, int defStyleRes) {
        super(context, attrs, defStyle, defStyleRes);
        init(context, attrs, defStyle, defStyleRes);
    }

    @Override
    public void onLocationChanged(AMapLocation amapLocation) {
        if (amapLocation.getErrorCode() == AMapLocation.LOCATION_SUCCESS) {
            if (!BestLocation.isIndoorValidate()) {
                double lat = amapLocation.getLatitude();
                double lng = amapLocation.getLongitude();
                if (amapLocation.getCoordType().equals(AMapLocation.COORD_TYPE_GCJ02)) {
                    double[] res = CoordsHelper.gcj02_To_Gps84(lat, lng);
                    lat = res[0]; lng = res[1];
                }
                BestLocation.updateGlobal(lat, lng, amapLocation.getAddress(), amapLocation.getTime());
                if (amapLocation.getBearing() != 0.0)
                    BestLocation.getInstance().direction = amapLocation.getBearing();
                GeoLocation p = getMyLocation();
                if (p != null && mPosListener.size() > 0 && isLocatorRunning) {
                    LocationEvent le = new LocationEvent(
                            BestLocation.getInstance().location,
                            BestLocation.getInstance().lng,
                            BestLocation.getInstance().lat,
                            BestLocation.getInstance().direction,
                            "",
                            "");
                    for (LocationListener listener : mPosListener) {
                        try {
                            listener.onLocation(le);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        } else {
            if (logEnable) {
                Log.e(TAG+"onLocationChanged","errCode:" + amapLocation.getErrorCode() + ",errInfo:" + amapLocation.getErrorInfo());
            }
        }
    }

    /**
     * 采用最好的方式获取定位信息
     */
    public GeoLocation getMyLocation() {
        GeoLocation p;
        if (BestLocation.isIndoorValidate()) {
            p = new GeoLocation();
            p.address = "室内位置";
            p.lng = BestLocation.getInstance().lng;
            p.lat = BestLocation.getInstance().lat;
            p.direction = BestLocation.getInstance().direction;
            p.buildingId = BestLocation.getInstance().buildingId;
            p.floorId = BestLocation.getInstance().floorId;
            p.time = new Date(BestLocation.getInstance().getUpdateTime());

            Common.getLogger(null).log(Level.INFO, String.format("getMyLocation: latitude: %f, longitude: %f, updateTime: %s, direction: %f, address: %s, buildingId: %s, floorId: %s",
                    p.lat, p.lng, formatter.format(p.time), p.direction, p.address, p.buildingId, p.floorId));
            return p;
        } else if (BestLocation.isGlobalValidate()) {
            p = new GeoLocation();
            p.address = BestLocation.getInstance().location;
            p.lng = BestLocation.getInstance().lng;
            p.lat = BestLocation.getInstance().lat;
            p.direction = BestLocation.getInstance().direction;
            p.buildingId = "";
            p.floorId = "";
            p.time = new Date(BestLocation.getInstance().getUpdateTime());

            if (p.lat != 0 && p.lng != 0) {
                Common.getLogger(null).log(Level.INFO, String.format("getMyLocation: latitude: %f, longitude: %f, updateTime: %s, direction: %f, address: %s, buildingId: %s, floorId: %s",
                        p.lat, p.lng, formatter.format(p.time), p.direction, p.address, p.buildingId, p.floorId));
                return p;
            }
        }

        Criteria c = new Criteria();//Criteria类是设置定位的标准信息（系统会根据你的要求，匹配最适合你的定位供应商），一个定位的辅助信息的类
        c.setPowerRequirement(Criteria.POWER_LOW);//设置低耗电
        c.setAltitudeRequired(true);//设置需要海拔
        c.setBearingAccuracy(Criteria.ACCURACY_COARSE);//设置COARSE精度标准
        c.setAccuracy(Criteria.ACCURACY_LOW);//设置低精度
        //... Criteria 还有其他属性，就不一一介绍了
        Location best = LocationUtils.getBestLocation(getContext(), c);
        if (best == null) {
            Location net = LocationUtils.getNetWorkLocation(getContext());
            if (net == null) {
                return null;
            } else {
                BestLocation.updateGlobal(net.getLatitude(), net.getLongitude(), "实时位置", net.getTime());
                p = new GeoLocation();
                p.address = "网络位置";
                p.lng = net.getLongitude();
                p.lat = net.getLatitude();
                p.direction = BestLocation.getInstance().direction;
                p.time = new Date(net.getTime());

                Common.getLogger(null).log(Level.INFO, String.format("getMyLocation: latitude: %f, longitude: %f, updateTime: %s, direction: %f, address: %s, buildingId: %s, floorId: %s",
                        p.lat, p.lng, formatter.format(p.time), p.direction, p.address, p.buildingId, p.floorId));
                return p;
            }
        } else {
            BestLocation.updateGlobal(best.getLatitude(), best.getLongitude(), "实时位置", best.getTime());
            p = new GeoLocation();
            p.address = "实时位置";
            p.lng = best.getLongitude();
            p.lat = best.getLatitude();
            p.direction = BestLocation.getInstance().direction;
            p.time = new Date(best.getTime());

            Common.getLogger(null).log(Level.INFO, String.format("getMyLocation: latitude: %f, longitude: %f, updateTime: %s, direction: %f, address: %s, buildingId: %s, floorId: %s",
                    p.lat, p.lng, formatter.format(p.time), p.direction, p.address, p.buildingId, p.floorId));
            return p;
        }
    }

    /**
     * 获取定位
     * @param context
     * @return
     */
    public GeoLocation getMyLocation(Context context) {
        if (_instance != null)
            return _instance.getMyLocation();

        GeoLocation p;
        if (BestLocation.isIndoorValidate()) {
            p = new GeoLocation();
            p.address = BestLocation.getInstance().location;
            p.lng = BestLocation.getInstance().lng;
            p.lat = BestLocation.getInstance().lat;
            p.direction = BestLocation.getInstance().direction;
            p.buildingId = BestLocation.getInstance().buildingId;
            p.floorId = BestLocation.getInstance().floorId;
            p.time = new Date(BestLocation.getInstance().getUpdateTime());

            Common.getLogger(null).log(Level.INFO, String.format("getMyLocation2: latitude: %f, longitude: %f, updateTime: %s, direction: %f, address: %s, buildingId: %s, floorId: %s",
                    p.lat, p.lng, formatter.format(p.time), p.direction, p.address, p.buildingId, p.floorId));
            return p;
        }
//        else if (BestLocation.isGlobalValidate()) {
//            p = new GeoLocation();
//            p.address = BestLocation.getInstance().location;
//            p.lng = BestLocation.getInstance().lng;
//            p.lat = BestLocation.getInstance().lat;
//            p.buildingId = "";
//            p.floorId = "";
//            p.time = new Date(BestLocation.getInstance().getUpdateTime());
//
//            return p;
//        }

        Criteria c = new Criteria();//Criteria类是设置定位的标准信息（系统会根据你的要求，匹配最适合你的定位供应商），一个定位的辅助信息的类
        c.setPowerRequirement(Criteria.POWER_LOW);//设置低耗电
        c.setAltitudeRequired(true);//设置需要海拔
        c.setBearingAccuracy(Criteria.ACCURACY_COARSE);//设置COARSE精度标准
        c.setAccuracy(Criteria.ACCURACY_LOW);//设置低精度
        //... Criteria 还有其他属性，就不一一介绍了
        Location best = LocationUtils.getBestLocation(context, c);
        if (best == null) {
            Location net = LocationUtils.getNetWorkLocation(context);
            if (net == null) {
                return null;
            } else {
                BestLocation.updateGlobal(net.getLatitude(), net.getLongitude(), "实时位置", net.getTime());
                p = new GeoLocation();
                p.address = "网络位置";
                p.lng = net.getLongitude();
                p.lat = net.getLatitude();
                p.direction = BestLocation.getInstance().direction;
                p.time = new Date(net.getTime());

                Common.getLogger(null).log(Level.INFO, String.format("getMyLocation2: latitude: %f, longitude: %f, updateTime: %s, direction: %f, address: %s, buildingId: %s, floorId: %s",
                        p.lat, p.lng, formatter.format(p.time), p.direction, p.address, p.buildingId, p.floorId));
                return p;
            }
        } else {
            BestLocation.updateGlobal(best.getLatitude(), best.getLongitude(), "实时位置", best.getTime());
            p = new GeoLocation();
            p.address = "实时位置";
            p.lng = best.getLongitude();
            p.lat = best.getLatitude();
            p.direction = BestLocation.getInstance().direction;
            p.time = new Date(best.getTime());

            Common.getLogger(null).log(Level.INFO, String.format("getMyLocation2: latitude: %f, longitude: %f, updateTime: %s, direction: %f, address: %s, buildingId: %s, floorId: %s",
                    p.lat, p.lng, formatter.format(p.time), p.direction, p.address, p.buildingId, p.floorId));
            return p;
        }
    }

    private void init(Context context, AttributeSet attrs, int defStyle, int defStyleRes) {
        _instance = this;
        View inflate = inflate(context, R.layout.gisview, this);
        mapView = inflate.findViewById(R.id.mapview);
        mapView.addMapViewEventListener(this);
        touchOverlay = new TouchOverlay();
        mapView.getOverlays().add(touchOverlay);

        namedOverlays = new HashMap<>();
        routeOverlay = new ArrayList<>();
        mMarkerListener = new ArrayList<>();
        mBuildingListener = new ArrayList<>();
        mModelListener = new ArrayList<>();
        mZoomListener = new ArrayList<>();
        mMapListener = new ArrayList<>();
        handler = new ExecuteFinished(this);
        heatmapList = new ArrayList<>();
        openIndoors = new HashMap<>();
        openBasements = new HashMap<>();
        openedMaps = new ArrayList<>();
        defaultFacilities = new HashMap<>();
        mZoomToIndoorListener = null;
        mCalculateRouteListener = null;
        pathPlanDataListener = null;
        mMapCacheListener = null;
        mMapLoadedListener = null;
        mMapMoveListener = new ArrayList<>();

        //初始化日志
        Common.getLogger(context).log(Level.INFO, "gis module init.");
        //初始化定位
        mLocationClient = new AMapLocationClient(context);
        //设置定位回调监听
        mLocationClient.setLocationListener(this);
        AMapLocationClientOption option = new AMapLocationClientOption();
        option.setLocationPurpose(AMapLocationClientOption.AMapLocationPurpose.Transport);
        option.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        option.setOnceLocation(false);
        option.setNeedAddress(true);
        option.setSensorEnable(true);
        option.setHttpTimeOut(10000);
        if(mLocationClient != null){
            mLocationClient.setLocationOption(option);
            //设置场景模式后最好调用一次stop，再调用start以保证场景模式生效
            mLocationClient.stopLocation();
//            mLocationClient.startLocation();
        }
    }

    /**
     * 设置地图宽高
     * @param width
     * @param height
     */
    public void setGisViewLayoutParams(int width, int height) {
        if (mapView != null) {
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(width, height);
            mapView.setLayoutParams(params);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mapView.setVisibility(GONE);
        mapView.destroy();
    }

    public static void setGisServer(String gisUrl) {
        Common.CreateInstance(gisUrl);
    }

    public void setRTLSServer(String rtlsUrl) {
        Common.initRtlsLicenseHost(rtlsUrl);
    }

    public void setFloorMapper(HashMap<String, String> mapper) {
        floorMapper = mapper;
    }

    public static void initEngine(Context context, String key, String secret,
                           String baseUrl, String evnUri, boolean isGate, String gateAppKey, String gateId, String gateUrl) {
        Common.getLogger(null).log(Level.INFO, String.format("Init iVAS: setBaseUrl=%s; setURI=%s; setIsGateEv=%s; setGateId=%s; setGateUrl=%s",
                baseUrl, evnUri, isGate ? "true" : "false", gateId, gateUrl));
        mPosListener = new ArrayList<>();
    }

    public void deinitEngine(){
        if (mLocationClient != null) {
            mLocationClient.stopLocation();
        }
    }

    /**
     * 查询地址相关地理信息
     * @param address
     * @param callback
     */
    public void getLocationOfAddress(final String address, final GeoServiceCallback callback) {
        getLocationOfAddress(address, -1, callback);
    }

    /**
     * 查询地址相关地理信息
     * @param address
     * @param count
     * @param callback
     */
    public void getLocationOfAddress(final String address,final int count, final GeoServiceCallback callback){
        new Thread(new Runnable() {
            @Override
            public void run() {
                String ret = "[]";
                try {
                    String url = String.format("%s%s?address=%s&fromIndex=0&toIndex=9999&maxReturn=%s", Common.getHost(), Common.GEO_CODE_URL(), address, count);
                    if (logEnable) {
                        Log.e(TAG + "getLocationOfAddress", url);
                    }
                    ret = getStringFromURL(url);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                JSONArray arr = new JSONArray();
                try {
                    arr = new JSONArray(ret);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                GeoLocation[] res = new GeoLocation[arr.length()];
                for (int i = 0; i < arr.length(); i++) {
                    res[i] = new GeoLocation();
                    try {
                        JSONObject obj = arr.getJSONObject(i);
                        res[i].address = obj.getString("address");
                        if (!TextUtils.isEmpty(res[i].address) && res[i].address.contains(",")) {
                            try {
                                String[] array = res[i].address.split(",");
                                res[i].parkId = array[0];
                                res[i].parkX = array[1];
                                res[i].parkY = array[2];
                                res[i].roomCode = array[3];
                                res[i].zoneX = array[4];
                                res[i].zoneY = array[5];
                                res[i].cnName = array[6];
                                res[i].enName = array[7];
                            } catch (ArrayIndexOutOfBoundsException e) {
                                e.printStackTrace();
                            }
                        }
                        res[i].lng = obj.getJSONObject("location").getDouble("x");
                        res[i].lat = obj.getJSONObject("location").getDouble("y");
                        res[i].score = obj.getDouble("score");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                if (callback != null)
                    callback.onQueryAddressFinished(res);

            }
        }).start();
    }

    /**
     * 通过经纬度获取地理信息
     * @param lng
     * @param lat
     * @param callback
     */
    public void getAddressOfLocation(final double lng, final double lat, final GeoServiceCallback callback) {
        getAddressOfLocation(lng, lat, -1, -1, callback);
    }

    /**
     * 通过经纬度获取地理信息
     * @param lng
     * @param lat
     * @param radius
     * @param count
     * @param callback
     */
    public void getAddressOfLocation(final double lng, final double lat,final double radius, final int count, final GeoServiceCallback callback){
        new Thread(new Runnable() {
            @Override
            public void run() {
                String ret = "[]";
                try {
                    String url = String.format("%s%s?x=%s&y=%s&geoDecodingRadius=%s&fromIndex=0&toIndex=9999&maxReturn=%s", Common.getHost(), Common.GEO_DECODE_URL(), lng, lat, radius, count);
                    if (logEnable) {
                        Log.e("getAddressOfLocation", url);
                    }
                    ret = getStringFromURL(url);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                JSONArray arr = new JSONArray();
                try {
                    arr = new JSONArray(ret);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                GeoLocation[] res = new GeoLocation[arr.length()];
                for (int i = 0; i < arr.length(); i++) {
                    res[i] = new GeoLocation();
                    try {
                        JSONObject obj = arr.getJSONObject(i);
                        res[i].address = obj.getString("address");
                        if (!TextUtils.isEmpty(res[i].address) && res[i].address.contains(",")) {
                            try {
                                String[] array = res[i].address.split(",");
                                res[i].parkId = array[0];
                                res[i].parkX = array[1];
                                res[i].parkY = array[2];
                                res[i].roomCode = array[3];
                                res[i].zoneX = array[4];
                                res[i].zoneY = array[5];
                                res[i].cnName = array[6];
                                res[i].enName = array[7];
                            } catch (ArrayIndexOutOfBoundsException e) {
                                e.printStackTrace();
                            }
                        }
                        res[i].lng = obj.getJSONObject("location").getDouble("x");
                        res[i].lat = obj.getJSONObject("location").getDouble("y");
                        res[i].score = obj.getDouble("score");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                if (callback != null)
                    callback.onQueryAddressFinished(res);

            }
        }).start();
    }

    /**
     * 通过经纬度获取地当前workspace
     * @param lng
     * @param lat
     * @param callback
     */
    public static void queryWorkspace(final double lng, final double lat, final QueryWorkspaceListener callback) {
        queryWorkspace(lng, lat, -1, -1, callback);
    }

    /**
     * 通过经纬度获取地当前workspace
     * @param lng
     * @param lat
     * @param radius
     * @param count
     * @param callback
     */
    public static void queryWorkspace(final double lng, final double lat,final double radius, final int count, final QueryWorkspaceListener callback){
        new Thread(new Runnable() {
            @Override
            public void run() {
                String ret = "[]";
                try {
                    //获取HWYQ数据
                    String url = String.format("%s%s?x=%s&y=%s&geoDecodingRadius=%s&fromIndex=0&toIndex=9999&maxReturn=%s", Common.getHost(), Common.GEO_DECODE_HWYQURL(), lng, lat, radius, count);
                    Log.e("switchWorkspace", url);
                    ret = getStringFromURL(url);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                JSONArray arr = new JSONArray();
                try {
                    arr = new JSONArray(ret);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                GeoLocation[] res = new GeoLocation[arr.length()];
                for (int i = 0; i < arr.length(); i++) {
                    res[i] = new GeoLocation();
                    try {
                        JSONObject obj = arr.getJSONObject(i);
                        res[i].address = obj.getString("address");
                        if (!TextUtils.isEmpty(res[i].address) && res[i].address.contains(",")) {
                            try {
                                String[] array = res[i].address.split(",");
                                res[i].parkId = array[0];
                                res[i].parkX = array[1];
                                res[i].parkY = array[2];
                                res[i].roomCode = array[3];
                                res[i].zoneX = array[4];
                                res[i].zoneY = array[5];
                                res[i].cnName = array[6];
                                res[i].enName = array[7];
                            } catch (ArrayIndexOutOfBoundsException e) {
                                e.printStackTrace();
                            }
                        }
                        res[i].lng = obj.getJSONObject("location").getDouble("x");
                        res[i].lat = obj.getJSONObject("location").getDouble("y");
                        res[i].score = obj.getDouble("score");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                if (callback != null)
                    callback.onQueryWorkspace(res);

            }
        }).start();
    }


    public static String getStringFromURL(String urlString) throws IOException {
        HttpClient httpclient = new DefaultHttpClient();
        HttpGet httpget = new HttpGet(urlString);
        HttpResponse response = httpclient.execute(httpget);
        if (response.getStatusLine().getStatusCode() == 200) {
            return EntityUtils.toString(response.getEntity());
        } else {
            return "[]";
        }
    }

    @Override
    public void onTap(Point2D point2D, MapView mapView) {
        List<Overlay> overlays = mapView.getOverlays();
        Rect rect = null;
        for (Overlay ov : overlays) {
            if (ov instanceof DefaultItemizedOverlay) {
                DefaultItemizedOverlay overlay = (DefaultItemizedOverlay) ov;
                if (overlay.getFocus() == null)
                    continue;

                MarkerEvent me = new MarkerEvent();
                me.eventType = TargetEvent.Press;
                me.position = new double[]{point2D.x, point2D.y};
                me.marker = ((OverlayItemEx) overlay.getFocus()).marker;
                me.width = me.marker.width;
                me.height = me.marker.height;
                me.tag = me.marker.tag;
                me.markerId = me.marker.markerId;

                Point base = mapView.toScreenPoint(new Point2D(me.marker.position[1], me.marker.position[0]));
                int wid = me.marker.width;
                int hei = me.marker.height;
                rect = new Rect(base.x - wid / 2, base.y - hei, base.x + wid / 2, base.y);

                for (MarkerListener listener : mMarkerListener) {
                    listener.markerEvent(me);
                }
                break;
            }
        }
        if (rect != null) {
            List<MarkerEvent> events = new ArrayList<>();
            for (Overlay ov : overlays) {
                if (ov instanceof DefaultItemizedOverlay) {
                    DefaultItemizedOverlay overlay = (DefaultItemizedOverlay) ov;

                    OverlayItemEx item = (OverlayItemEx) overlay.getItem(0);
                    Point base = mapView.toScreenPoint(new Point2D(item.marker.position[1], item.marker.position[0]));
                    int wid = item.marker.width;
                    int hei = item.marker.height;
                    Rect self = new Rect(base.x - wid / 2, base.y - hei, base.x + wid / 2, base.y);

                    if (!(rect.left > self.right || rect.right < self.left || rect.top > self.bottom || rect.bottom < self.top)) {
                        MarkerEvent me = new MarkerEvent();
                        me.eventType = TargetEvent.Press;
                        me.position = new double[]{point2D.x, point2D.y};
                        me.marker = item.marker;
                        me.width = me.marker.width;
                        me.height = me.marker.height;
                        me.tag = me.marker.tag;
                        me.markerId = me.marker.markerId;
                        events.add(me);
                    }
                }
            }
            for (MarkerListener listener : mMarkerListener) {
                listener.markerEvent(events.toArray(new MarkerEvent[0]));
            }
        }
    }

    @Override
    public void onTap(Point2D point2D, Overlay overlay, MapView mapView) {
        if (overlay instanceof PolygonOverlay) {
            if (mBuildingListener.size() > 0) {
                String ovKey = overlay.getKey();
                if (TextUtils.isEmpty(ovKey) || !ovKey.startsWith("building:"))
                    return;

                List<Overlay> overlays = mapView.getOverlays();
                for (Overlay ov : overlays) {
                    String key = ov.getKey();
                    if (!TextUtils.isEmpty(key) && key.startsWith("building:")) {
                        PolygonOverlay unselect = (PolygonOverlay) ov;
                        if (unselect != null)
                            unselect.setLinePaint(getBuildingSelectPaint(false));
                    }
                }
                PolygonOverlay ov = (PolygonOverlay) overlay;
                ov.setLinePaint(getBuildingSelectPaint(true));
                mapView.invalidate();

                if (buildings != null) {
                    for (QueryUtils.BuildingResult br : buildings) {
                        String k = String.format("building:[%d]", br.feature.getID());
                        if (ovKey.equalsIgnoreCase(k)) {
                            Point point = new Point();
                            mapView.getProjection().toPixels(point2D, point);
                            BuildingEvent be = new BuildingEvent(
                                    TargetEvent.Press,
                                    new double[]{point.x, point.y},
                                    br.feature.fieldNames,
                                    br.feature.fieldValues);
                            for (BuildingListener listener : mBuildingListener) {
                                listener.buildingEvent(be);
                            }
                        }
                    }
                }
            }
            if (mModelListener.size() > 0) {
                Iterator iter = openIndoors.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry<String, IndoorMapData> entry = (Map.Entry<String, IndoorMapData>) iter.next();
                    IndoorMapData indoorMapData = entry.getValue();
                    for (ModelData modelData : indoorMapData.rooms) {
                        boolean isHit;
                        if (modelData.geometry != null) {
                            for (List<Point2D> geoPoints : modelData.geometry) {
                                isHit = QueryUtils.isInPolygon(point2D, geoPoints.toArray(new Point2D[0]));
                                if (isHit) {
                                    ModelEvent me = new ModelEvent(
                                            TargetEvent.Press,
                                            new double[]{point2D.x, point2D.y},
                                            modelData.features);
                                    for (ModelListener listener : mModelListener)
                                        listener.modelEvent(me);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void mapLoaded(MapView mapView) {
    }

    @Override
    public void longTouch(MapView mapView) {
        fitMapWithNoBlank(mapView);
    }

    @Override
    public void touch(MapView mapView) {
    }

    @Override
    public void moveStart(MapView mapView) {
        fitHeatmapToView(false);
        indoorDetect();
        refreshOpenMaps();
//        if (mMapMoveListener!= null && mMapMoveListener.size() > 0) {
//            if (mapView != null) {
//                double[] bounds = new double[4];
//                BoundingBox bx = mapView.getViewBounds();
//                bounds[0] = bx.getTop();
//                bounds [1] = bx.getLeft();
//                bounds[2] = bx.getBottom();
//                bounds[3] = bx.getRight();
//                Point2D point2D = mapView.getCenter();
//                for (MapMoveListener listener : mMapMoveListener) {
//                    listener.mapMove(new MapMoveEvent(bounds, point2D.y, point2D.x));
//                }
//            }
//        }
    }

    @Override
    public void move(MapView mapView) {
        fitMapWithNoBlank(mapView);
        indoorDetect();
        refreshOpenMaps();
//        if (mMapMoveListener!= null && mMapMoveListener.size() > 0) {
//            if (mapView != null) {
//                double[] bounds = new double[4];
//                BoundingBox bx = mapView.getViewBounds();
//                bounds[0] = bx.getTop();
//                bounds [1] = bx.getLeft();
//                bounds[2] = bx.getBottom();
//                bounds[3] = bx.getRight();
//                Point2D point2D = mapView.getCenter();
//                for (MapMoveListener listener : mMapMoveListener) {
//                    listener.mapMove(new MapMoveEvent(bounds, point2D.y, point2D.x));
//                }
//            }
//        }
    }

    @Override
    public void moveEnd(MapView mapView) {
        fitMapWithNoBlank(mapView);
        indoorDetect();
        refreshOpenMaps();
        if (mMapMoveListener!= null && mMapMoveListener.size() > 0) {
            if (mapView != null) {
                double[] bounds = new double[4];
                BoundingBox bx = mapView.getViewBounds();
                bounds[0] = bx.getTop();
                bounds [1] = bx.getLeft();
                bounds[2] = bx.getBottom();
                bounds[3] = bx.getRight();
                Point2D point2D = mapView.getCenter();
                for (MapMoveListener listener : mMapMoveListener) {
                    listener.mapMove(new MapMoveEvent(bounds, point2D.y, point2D.x));
                }
            }
        }
    }

    @Override
    public void zoomStart(MapView mapView) {
        fitHeatmapToView(true);
        ZoomEvent ze = new ZoomEvent(Zoom.ZoomStart, mapView.getZoomLevel());
        for (ZoomListener listener : mZoomListener) {
            listener.zoomEvent(ze);
        }
    }

    @Override
    public void zoomEnd(MapView mapView) {
//        System.out.print(String.format("---> cur:%d\n", mapView.getZoomLevel()));
        fitHeatmapToView(true);
        switchMarkerHide();
        ZoomEvent ze = new ZoomEvent(Zoom.ZoomEnd, mapView.getZoomLevel());
        for (ZoomListener listener : mZoomListener) {
            listener.zoomEvent(ze);
        }
        fitMapWithNoBlank(mapView);
        indoorDetect();
        refreshOpenMaps();
        mapView.invalidate();
    }

    private void indoorDetect() {
        if (indoorZoomLevel > 0) {
            int curZoomLevel = mapView.getZoomLevel();
            if (curZoomLevel >= indoorZoomLevel/* && (lastZoomLevel == -1 || lastZoomLevel < indoorZoomLevel)*/) {
//                System.out.print(String.format("---> cur:%d, last:%d, set:%d\n", curZoomLevel, lastZoomLevel, indoorZoomLevel));
                Point2D point2D = mapView.getCenter();
                String buildingId = "";
                String floorId = "";
                String floorList = "";
                if (buildings != null) {
                    for (QueryUtils.BuildingResult br : buildings) {
                        if (QueryUtils.isInPolygon(point2D, br.buildingGeometry.toArray(new Point2D[0]))) {
                            for (int i = 0; i < br.feature.fieldNames.length; i++) {
//                            System.out.print(String.format("---> field:%s, value:%s\n", br.feature.fieldNames[i], br.feature.fieldValues[i]));
                                if (br.feature.fieldNames[i].equalsIgnoreCase("BUILDINGID"))
                                    buildingId = br.feature.fieldValues[i];
                                if (br.feature.fieldNames[i].equalsIgnoreCase("FLOORLIST"))
                                    floorList = br.feature.fieldValues[i];
                                if (!TextUtils.isEmpty(buildingId) && !TextUtils.isEmpty(floorList))
                                    break;
                            }
                            break;
                        }
                    }
                }
                if (!TextUtils.isEmpty(buildingId)) {
//                    System.out.print(String.format("---> field:%s\n", buildingId));
                    if (TextUtils.isEmpty(currIndoorDetect) || !currIndoorDetect.equalsIgnoreCase(buildingId)) {
                        currIndoorDetect = buildingId;
                        if (mZoomToIndoorListener != null) {
                            ZoomToIndoorEvent ztie = new ZoomToIndoorEvent();
                            ztie.zoomLevel = curZoomLevel;
                            ztie.center = new double[2];
                            ztie.center[0] = point2D.y;
                            ztie.center[1] = point2D.x;
                            ztie.buildingId = buildingId;
                            ztie.floorList = floorList;
                            ztie.floorId = "";
                            for (String item : openedMaps) {
                                if (item.startsWith(buildingId)) {
                                    ztie.floorId = item.substring(item.indexOf(':')+1);
                                    break;
                                }
                            }
                            if (TextUtils.isEmpty(ztie.floorId)) {
                                for (String item : openedMaps) {
                                    if (item.startsWith("Basement")) {
                                        ztie.floorId = item.substring(item.indexOf(':')+1);
                                        break;
                                    }
                                }
                            }
                            mZoomToIndoorListener.zoomEvent(ztie);
                        } else {
                            showIndoorMap(buildingId, defaultZoomIndoor);
                        }
                    }
                } else {
                    if (!TextUtils.isEmpty(currIndoorDetect)) {
                        currIndoorDetect = "";
                        if (mZoomToIndoorListener != null) {
                            ZoomToIndoorEvent ztie = new ZoomToIndoorEvent();
                            ztie.zoomLevel = curZoomLevel;
                            ztie.center = new double[2];
                            ztie.center[0] = point2D.y;
                            ztie.center[1] = point2D.x;
                            ztie.buildingId = "";
                            ztie.floorList = "";
                            ztie.floorId = "";
                            mZoomToIndoorListener.zoomEvent(ztie);
                        }
                    }
                }
            }
            if (curZoomLevel < indoorZoomLevel/* && (lastZoomLevel == -1 || lastZoomLevel>= indoorZoomLevel)*/) {
//                System.out.print(String.format("---> cancel cur:%d, last:%d, set:%d\n", curZoomLevel, lastZoomLevel, indoorZoomLevel));
                switchOutdoor();
            }
        }
        lastZoomLevel = mapView.getZoomLevel();
    }

    private void refreshOpenMaps() {
        Iterator iter = openIndoors.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, IndoorMapData> entry = (Map.Entry<String, IndoorMapData>) iter.next();
            IndoorMapData indoorMapData = entry.getValue();
            Message msg = new Message();
            msg.obj = indoorMapData;
            msg.what = Common.QUERY_INDOOR_MAP;
            handler.sendMessage(msg);
        }
        iter = openBasements.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, QueryUtils.BasementMapResult> entry = (Map.Entry<String, QueryUtils.BasementMapResult>) iter.next();
            QueryUtils.BasementMapResult basementData = entry.getValue();
            Message msg = new Message();
            msg.obj = basementData;
            msg.what = Common.QUERY_BASEMENT_MAP;
            handler.sendMessage(msg);
        }
    }

    private void fitMapWithNoBlank(MapView mapView) {
        if (!isMapLoaded)
            return;

        BoundingBox viewBounds = null;
        BoundingBox mapBounds = null;
        try {
            viewBounds = mapView.getViewBounds();
            mapBounds = mapView.getBounds();
        } catch (Exception ex) {}
        if (viewBounds == null || mapBounds == null)
            return;

        boolean needReset = false;
        if (viewBounds.getWidth() > mapBounds.getWidth()) {
            double ratio = viewBounds.getHeight() / viewBounds.getWidth();
            viewBounds.leftTop.x = mapBounds.leftTop.x;
            viewBounds.rightBottom.x = mapBounds.rightBottom.x;
            double height = viewBounds.getWidth() * ratio;
            double c = viewBounds.getCenter().y;
            viewBounds.leftTop.y = c + height / 2;
            viewBounds.rightBottom.y = c - height / 2;
            needReset = true;
        } else if (viewBounds.getWidth() < mapBounds.getWidth()) {
            if (viewBounds.getLeft() < mapBounds.getLeft()) {
                double diff = mapBounds.getLeft() - viewBounds.getLeft();
                viewBounds.leftTop.x += diff;
                viewBounds.rightBottom.x += diff;
                needReset = true;
            }
            if (viewBounds.getRight() > mapBounds.getRight()) {
                double diff = viewBounds.getRight() - mapBounds.getRight();
                viewBounds.rightBottom.x -= diff;
                viewBounds.leftTop.x -= diff;
                needReset = true;
            }
        }
        if (viewBounds.getHeight() > mapBounds.getHeight()) {
            double ratio = viewBounds.getHeight() / viewBounds.getWidth();
            viewBounds.leftTop.y = mapBounds.leftTop.y;
            viewBounds.rightBottom.y = mapBounds.rightBottom.y;
            double width = viewBounds.getHeight() / ratio;
            double c = viewBounds.getCenter().x;
            viewBounds.leftTop.x = c - width / 2;
            viewBounds.rightBottom.x = c + width / 2;
            needReset = true;
        } else if (viewBounds.getHeight() < mapBounds.getHeight()) {
            if (viewBounds.getTop() > mapBounds.getTop()) {
                Log.d("----", "21");
                double diff = viewBounds.getTop() - mapBounds.getTop();
                viewBounds.leftTop.y -= diff;
                viewBounds.rightBottom.y -= diff;
                needReset = true;
            }
            if (viewBounds.getBottom() < mapBounds.getBottom()) {
                Log.d("----", "22");
                double diff = mapBounds.getBottom() - viewBounds.getBottom();
                viewBounds.rightBottom.y += diff;
                viewBounds.leftTop.y += diff;
                needReset = true;
            }
        }
        if (needReset)
            mapView.setViewBounds(viewBounds);
    }

    private void fitHeatmapToView(boolean recalc) {
        if (heatmapList.size() > 0) {
            for (HeatmapDrawable hp : heatmapList) {
                Point leftTop = new Point();
                Point rightBottom = new Point();
                mapView.getProjection().toPixels(new Point2D(hp.geoLeft, hp.geoTop), leftTop);
                mapView.getProjection().toPixels(new Point2D(hp.geoRight, hp.geoBottom), rightBottom);
                hp.fitToMapView(leftTop.x, leftTop.y, rightBottom.x, rightBottom.y, recalc);
            }
        }
    }

    class TouchOverlay extends Overlay {
        private long touchTime = -1;
        @Override
        public boolean onTouchEvent(MotionEvent event, final MapView mapView) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                touchTime = (new Date()).getTime();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                final int touchX = Math.round(event.getX());
                final int touchY = Math.round(event.getY());
                // 记录点击位置
                final Point2D touchPoint = mapView.getProjection().fromPixels(touchX, touchY);
                long time = (new Date()).getTime();
                if (!(touchTime > -1 && time - touchTime > 2000)) {
                    MapEvent me = new MapEvent(TargetEvent.Press,
                            new int[]{touchX, touchY},
                            new double[]{touchPoint.y, touchPoint.x},
                            new String[0]);
                    Message msg = new Message();
                    msg.obj = me;
                    msg.what = Common.EVENT_MAP_TAP;
                    handler.sendMessage(msg);
                    return false;
                }
                if (mMapListener.size() > 0) {
                    // 获取地理信息
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            String ret = "[]";
                            try {
                                String url = Common.getHost() + Common.GEO_DECODE_URL() + "?x=" + touchPoint.x + "&y=" + touchPoint.y + "&geoDecodingRadius=" + 0.0001 + "&fromIndex=0&toIndex=10&maxReturn=" + 5;
                                ret = GisView.getStringFromURL(url);
                            } catch (Exception ex) {
                                Log.d(TAG, "getLocationOfAddress: " + ex.getMessage());
                            }

                            JSONArray arr = new JSONArray();
                            try {
                                arr = new JSONArray(ret);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            String[] addrs = new String[arr.length()];

                            for (int i = 0; i < arr.length(); i++) {
                                try {
                                    JSONObject obj = arr.getJSONObject(i);
                                    addrs[i] = obj.getString("address");
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            // 引发事件
                            MapEvent me = new MapEvent(TargetEvent.LongPress,
                                    new int[]{touchX, touchY},
                                    new double[]{touchPoint.y, touchPoint.x},
                                    addrs);
                            Message msg = new Message();
                            msg.obj = me;
                            msg.what = Common.EVENT_MAP_TAP;
                            handler.sendMessage(msg);
                        }
                    }).start();
                }
            }

            return false;
        }
    }

    public static void enableAutoClearCache(boolean enable) {
        autoClearCachedTiles = enable;
    }

    public void clearMapCache() {
        mapView.clearTilesInDB();
        File dir = getContext().getExternalFilesDir("Maps");
        File[] files = dir.listFiles();
        for (File file : files)
            if (file.exists())
                file.delete();
    }

    public void addMapCacheListener(MapCacheListener listener) {
        mMapCacheListener = listener;
    }

    public void removeMapCacheListener() {
        mMapCacheListener = null;
    }

    public void addMapLoadedListener(MapLoadedListener listener) {
        mMapLoadedListener = listener;
    }

    public void removeMapLoadedListener() {
        mMapLoadedListener = null;
    }

    public void addMapMoveListener(MapMoveListener listener) {
        if (mMapMoveListener != null) {
            mMapMoveListener.add(listener);
        }
    }

    public void removeMapMoveListener(MapMoveListener listener) {
        if (mMapMoveListener != null) {
            mMapMoveListener.remove(listener);
        }
    }

    /**
     * 加载地图
     * @param zoom 地图缩放级别
     * @param center 经纬度中心点
     * @param parkId 园区id
     * @return
     */
    public boolean loadMap(int zoom, double[] center, String parkId) {
        if (!parkId.equalsIgnoreCase(Common.parkId())) {
            if (autoClearCachedTiles) {
                clearMapCache();
            }
            destroyMap();
        }
        loadOffLineMaps(zoom, center, parkId);
        return true;
    }

    private void loadOffLineMaps(int zoom, double[] center, String parkId) {
        setZoom(center, zoom);
        if (mapLayer == null) {
            mapLayer = new LayerView(getContext());
            mapLayer.setURL(Common.getHost() + Common.MAP_URL());
            mapView.addLayer(mapLayer);
            handler.sendEmptyMessage(Common.START_TIMER);
            QueryUtils.queryAllBuildings("buildings@" + Common.parkId(), handler);
        }

        //放到这里调用是为了先获取图层瓦片BTYQ数据，然后才是获取TA相关数据
        Common.setCurrentZone(parkId, parkId);
    }

    public void setGlobalZone(String workspace, String datasource, String dataset) {
        Common.setGlobalZone(workspace, datasource, dataset);
    }

    public void setParkKeys(String idName, String centerXName, String centerYName) {
        Common.setParkKeys(idName, centerXName, centerYName);
    }

    public String getWorkSpace() {
        return Common.workSpace();
    }

    public String getParkId() {
        return Common.parkId();
    }

    /**
     * 设置地图中心点
     * @param lat
     * @param lng
     */
    public void setCenter(double lat, double lng) {
        MapController controller = mapView.getController();
        controller.setCenter(new Point2D(lng, lat));
        if (logEnable) {
            Log.e(TAG+"setCenter", "lat="+lat+",lng="+lng);
        }
        fitHeatmapToView(false);
    }

    /**
     * 获取地图边界
     * @return
     */
    public double[] getMapBounds() {
        double[] vars = new double[4];
        if (mapView != null) {
            BoundingBox bx = mapView.getViewBounds();
            vars[0] = bx.getTop();
            vars[1] = bx.getLeft();
            vars[2] = bx.getBottom();
            vars[3] = bx.getRight();
            return vars;
        } else
            return null;
    }

    /**
     * 获取地图中心点
     * @return
     */
    public double[] getCenter() {
        Point2D point2D = mapView.getCenter();
        double[] result = new double[2];
        result[0] = point2D.y;
        result[1] = point2D.x;
        return result;
    }

    /**
     * 设置地图缩放值
     * @param zoom 地图缩放值
     */
    public void setZoom(int zoom) {
        MapController controller = mapView.getController();
        controller.setZoom(zoom);
    }

    /**
     * 设置地图缩放值
     * @param center 地图中心点
     * @param zoom 地图缩放值
     */
    public void setZoom(double[] center, int zoom) {
        MapController controller = mapView.getController();
        controller.setCenter(new Point2D(center[1], center[0]));
        controller.setZoom(zoom);
    }

    /**
     * 设置地图最大缩放值
     * @param level
     */
    public void setMaxZoomLevel(int level) {
        maxZoomLevel = level;
    }

    /**
     * 地图放大
     */
    public void zoomInMap() {
        mapView.zoomIn();
        if (logEnable) {
            Log.e(TAG+"zoomInMap", mapView.getZoomLevel()+"");
        }
    }

    /**
     * 地图缩小
     */
    public void zoomOutMap() {
        mapView.zoomOut();
    }

    public int getZoom() {
        return mapView.getZoomLevel();
    }

    /**
     * 设置室内地图参数
     * @param zoomLevel 缩放级别
     * @param listener 缩放监听事件
     * @param defaultZoomIndoor 楼层id
     */
    public void setSwitchIndoor(int zoomLevel, ZoomToIndoorListener listener, String defaultZoomIndoor) {
        this.indoorZoomLevel = zoomLevel;
        this.mZoomToIndoorListener = listener;
        if (!TextUtils.isEmpty(defaultZoomIndoor))
            this.defaultZoomIndoor = defaultZoomIndoor;
    }

    /**
     * 销毁地图
     */
    public void destroyMap() {
        GisDataCache.clearCaches();
        handler.removeMessages(Common.START_TIMER);
        clearOpensMap();
        mapView.removeAllLayers();
        mapView.getOverlays().add(touchOverlay);
        if (mapLayer != null) {
            mapLayer.destroy();
            mapLayer = null;
        }
        if (buildings != null) {
            buildings.clear();
            buildings = null;
        }
        for (String key : namedOverlays.keySet()) {
            List<Overlay> overlays = namedOverlays.get(key);
            for (Overlay ov : overlays)
                ov.destroy();
            overlays.clear();
        }
        namedOverlays.clear();
        for (LineOverlay ov : routeOverlay) {
            ov.destroy();
        }
        routeOverlay.clear();
        heatmapList.clear();
        currIndoorDetect = "";
        calculatedRoute = null;
        mapView.clearTilesInMemory();
        System.gc();
        isMapLoaded = false;
    }

    /**
     * 改变marker位置
     * @param markerId
     * @param lat
     * @param lng
     */
    public void changeMarkerPosition(String markerId, double lat, double lng) {
        changeMarkerPosition(markerId, lat, lng, null);
    }

    /**
     * 改变marker位置
     * @param markerId
     * @param lat
     * @param lng
     * @param marker
     */
    public void changeMarkerPosition(String markerId, double lat, double lng, Drawable marker) {
        List<Overlay> ovls = mapView.getOverlays();
        for (Overlay ov : ovls) {
            if (ov instanceof DefaultItemizedOverlay) {
                DefaultItemizedOverlay overlay = (DefaultItemizedOverlay) ov;
                for (int i = 0; i < overlay.size(); i++) {
                    OverlayItemEx item = (OverlayItemEx) overlay.getItem(i);
                    if (item.getTitle().equalsIgnoreCase(markerId)) {
                        item.setPoint(new Point2D(lng, lat));
                        if (marker != null)
                            item.setMarker(marker);
                        mapView.invalidate();
                        return;
                    }
                }
            }
        }
    }

    public void addMarker(String layerId, int layerIndex, GeneralMarker[] markers) {
        List<GeneralMarker> drawableMarkers = new ArrayList<>();
        List<GeneralMarker> urlMarkers = new ArrayList<>();
        for (GeneralMarker marker : markers) {
            if (marker.image == null) {
                if (!TextUtils.isEmpty(marker.imagePath))
                    urlMarkers.add(marker);
            } else
                drawableMarkers.add(marker);
        }

        if (drawableMarkers.size() > 0) {
            DefaultItemizedOverlay overlay = new DefaultItemizedOverlay(null);
            for (GeneralMarker marker : drawableMarkers) {
                OverlayItemEx item = new OverlayItemEx(new Point2D(marker.position[1], marker.position[0]), marker.markerId, marker.markerId, marker);
                MarkerDrawable drawable = new MarkerDrawable(marker.image, marker.width, marker.height);
                item.setMarker(drawable);
                overlay.addItem(item);
            }
            overlay.setTapListener(this);
            overlay.setZIndex(9000 + mapView.getOverlays().size());
            mapView.getOverlays().add(overlay);
            if (namedOverlays.containsKey(layerId))
                namedOverlays.get(layerId).add(overlay);
            else {
                List<Overlay> ovlys = new ArrayList<>();
                ovlys.add(overlay);
                namedOverlays.put(layerId, ovlys);
            }
            mapView.invalidate();
        }
        if (urlMarkers.size() > 0) {
            UrlMarkerMaker.makeUrlMarker(
                    layerId,
                    layerIndex,
                    urlMarkers.toArray(new GeneralMarker[urlMarkers.size()]),
                    handler);
        }
    }

    public void addMarker(String layerId, int layerIndex, FlashMarker[] markers) {
        List<FlashMarker> drawableMarkers = new ArrayList<>();
        List<FlashMarker> urlMarkers = new ArrayList<>();
        for (FlashMarker marker : markers) {
            if (marker.images == null) {
                if (marker.imagesPath != null)
                    urlMarkers.add(marker);
            } else
                drawableMarkers.add(marker);
        }

        if (drawableMarkers.size() > 0) {
            DefaultItemizedOverlay overlay = new DefaultItemizedOverlay(null);
            final List<MarkerAnimation> anilst = new ArrayList<>();
            for (FlashMarker marker : markers) {
                MarkerAnimation animation = new MarkerAnimation();
                for (Drawable d : marker.images) {
                    animation.addFrame(d);
                }
                animation.setNewSize(marker.width, marker.height);
                animation.setInterval(marker.interval);
                animation.setDuration(marker.duration);
                animation.setCallback(new Drawable.Callback() {
                    @Override
                    public void invalidateDrawable(@NonNull Drawable who) {
                        mapView.post(new Runnable() {
                            @Override
                            public void run() {
                                mapView.invalidate();
                            }
                        });
                    }

                    @Override
                    public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
                        mapView.post(new Runnable() {
                            @Override
                            public void run() {
                                mapView.invalidate();
                            }
                        });
                    }

                    @Override
                    public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
                        mapView.post(new Runnable() {
                            @Override
                            public void run() {
                                mapView.invalidate();
                            }
                        });
                    }
                });
                anilst.add(animation);
                OverlayItemEx item = new OverlayItemEx(new Point2D(marker.position[1], marker.position[0]), marker.markerId, marker.markerId, marker);
                item.setMarker(animation);
                overlay.addItem(item);
            }
            overlay.setTapListener(this);
            overlay.setZIndex(9000 + mapView.getOverlays().size());
            mapView.getOverlays().add(overlay);
            if (namedOverlays.containsKey(layerId))
                namedOverlays.get(layerId).add(overlay);
            else {
                List<Overlay> ovlys = new ArrayList<>();
                ovlys.add(overlay);
                namedOverlays.put(layerId, ovlys);
            }
            mapView.invalidate();
            mapView.post(new Runnable() {
                @Override
                public void run() {
                    for (MarkerAnimation ani : anilst) {
                        ani.start();
                    }
                }
            });
        }
        if (urlMarkers.size() > 0) {
            UrlMarkerMaker.makeUrlMarker(
                    layerId,
                    layerIndex,
                    urlMarkers.toArray(new FlashMarker[urlMarkers.size()]),
                    handler);
        }
    }

    /**
     * 绘制帧动画marker
     * @param layerId
     * @param layerIndex
     * @param markers
     */
    public void addFlashMarker(String layerId, int layerIndex, FlashMarker[] markers) {
        addMarker(layerId, layerIndex, markers);
    }

    /**
     * 删除marker
     * @param markerId
     */
    public void deleteMarker(String markerId) {
        List<Overlay> ovls = mapView.getOverlays();
        for (Overlay ov : ovls) {
            if (ov instanceof DefaultItemizedOverlay) {
                DefaultItemizedOverlay overlay = (DefaultItemizedOverlay) ov;
                for (int i = 0; i < overlay.size(); i++) {
                    OverlayItemEx item = (OverlayItemEx) overlay.getItem(i);
                    if (item.getTitle().equalsIgnoreCase(markerId)) {
                        overlay.removeItem(item);
                        mapView.getOverlays().remove(overlay);
                        mapView.invalidate();
                        return;
                    }
                }
            }
        }
    }

    /**
     * 删除图层
     * @param layerId 图层id
     */
    public void deleteLayer(String layerId) {
        List<Overlay> ovlys = namedOverlays.get(layerId);
        if (ovlys != null && ovlys.size() > 0) {
            for (Overlay ov : ovlys)
                mapView.getOverlays().remove(ov);
            mapView.invalidate();
            namedOverlays.remove(layerId);
        }
    }

    public void addMarkerListener(MarkerListener listener) {
        if (!mMarkerListener.contains(listener))
            mMarkerListener.add(listener);
    }

    public void removeMarkerListener(MarkerListener listener) {
        mMarkerListener.remove(listener);
    }

    /**
     * 绘制气泡
     * @param position
     * @param content
     * @param offset
     * @param width
     * @param height
     * @param tag
     * @return
     */
    public Object addPopup(double[] position, String content, double[] offset, int width, int height, Object tag) {
        SimplePopupOverlay overlay = new SimplePopupOverlay();
        overlay.setCoord(position);
        overlay.setContent(content);
        overlay.setOffset(offset);
        overlay.setSize(width, height);
        overlay.setTag(tag);
        overlay.setZIndex(9000 + mapView.getOverlays().size());
        mapView.getOverlays().add(overlay);
        mapView.invalidate();
        return overlay;
    }

    public Object addPopup(double[] position, View popupView, double[] offset, int width, int height, Object tag) {
        PopupOverlay overlay = new PopupOverlay();
        overlay.setCoord(position);
        overlay.setView(popupView);
        overlay.setOffset(offset);
        overlay.setTag(tag);
        overlay.setZIndex(9000 + mapView.getOverlays().size());
        mapView.addView(overlay.popView);
        mapView.getOverlays().add(overlay);
        mapView.invalidate();
        return overlay;
    }

    /**
     * 关闭气泡
     */
    public void closePopup() {
        List<Overlay> ovlys = mapView.getOverlays();
        List<Overlay> ovdels = new ArrayList<>();
        if (ovlys != null && ovlys.size() > 0) {
            for (Overlay ov : ovlys) {
                if (ov instanceof SimplePopupOverlay || ov instanceof PopupOverlay){
                    ovdels.add(ov);
                }
            }
            for (Overlay ov : ovdels)
                mapView.getOverlays().remove(ov);
            ovdels.clear();
            mapView.invalidate();
        }
    }
    public void closePopup(Object obj) {
        List<Overlay> ovlys = mapView.getOverlays();
        Overlay olay = (Overlay)obj;
        if (ovlys != null && ovlys.size() > 0) {
            for (Overlay ov : ovlys) {
                if (ov.getKey() == olay.getKey()){
                    mapView.getOverlays().remove(ov);
                    mapView.invalidate();
                    break;
                }
            }
        }
    }


    public static final String TYPE_NORMAL = "type_normal";//普通类型
    public static final String TYPE_PARKING = "type_parking";//停车位类型

    /**
     * 绘制室内地图
     * @param buildingId
     * @param floorid
     */
    public void showIndoorMap(String buildingId, String floorid) {
        showIndoorMap(buildingId, floorid, null);
    }

    /**
     * 绘制室内地图
     * @param buildingId
     * @param floorid
     */
    public void showIndoorMap(String buildingId, String floorid, IndoorCallback callback) {
        showIndoorMap(TYPE_NORMAL, buildingId, floorid, callback);
    }

    /**
     * 绘制室内地图
     * @param indoorType 室内地图样式，"type_normal";//普通类型   "type_parking";//停车位类型
     * @param buildingId
     * @param floorid
     */
    public void showIndoorMap(String indoorType, String buildingId, String floorid, IndoorCallback callback) {
        indoorCallback = callback;
        Common.getLogger(null).log(Level.INFO, String.format("showIndoorMap: buildingId=%s; floorid=%s", buildingId, floorid));
        if (TextUtils.isEmpty(floorid)) {
            clearOpensMap();
            mapView.invalidate();
        } else {
            String realBuildingId = buildingId;

            //转换之前需要完成的工作
            String newIndoorMap;
            if (TextUtils.isEmpty(realBuildingId))
                newIndoorMap = "Basement:" + floorid;
            else
                newIndoorMap = realBuildingId + ":" + floorid;
            Iterator<String> iterator = openedMaps.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().equalsIgnoreCase(newIndoorMap))
                    return;
            }
            clearOpensMap();
            currIndoorMap = newIndoorMap;
            //转换之前需要完成的工作

            BuildingConvertMappingData data = GisDataCache.getInstance(getContext(), this.mMapCacheListener).getBuidingConver(buildingId, floorid);
            if (data != null) {
                realBuildingId = data.targetId;
                Common.getLogger(null).log(Level.INFO, String.format("showIndoorMap convert to: buildingId=%s; floorid=%s", realBuildingId, floorid));
            }

            if (TextUtils.equals(indoorType, TYPE_PARKING)) {//室内停车场
//                QueryUtils.BasementMapResult basementMapResult = GisDataCache.getBasement(floorid);
//                if (basementMapResult != null) {
//                    Message msg = new Message();
//                    msg.obj = basementMapResult;
//                    msg.what = Common.QUERY_BASEMENT_MAP;
//                    handler.sendMessage(msg);
//                } else {
                    QueryUtils.queryBasementMap(Common.parkId(), realBuildingId, floorid, handler);
//                }
            } else {//室内地图正常样式
//                Feature buildingFeature = GisDataCache.getInstance(this.getContext(), this.mMapCacheListener).getBuilding(realBuildingId);
//                Feature[] floorFeatures = GisDataCache.getInstance(this.getContext(), this.mMapCacheListener).getFloor(realBuildingId, floorid);
//                if (floorFeatures != null && floorFeatures.length > 0 ) {
//                    List<List<Point2D>> buildingGeometry = null;
//                    if (buildingFeature != null && buildingFeature.geometry != null) {
//                        buildingGeometry = new ArrayList<>();
//                        int index = 0;
//                        for (int i=0; i<buildingFeature.geometry.parts.length; i++) {
//                            List<Point2D> point2DS = new ArrayList<>();
//                            for (int j=0; j<buildingFeature.geometry.parts[i]; j++) {
//                                Point2D point2D = new Point2D();
//                                point2D.x = buildingFeature.geometry.points[index].x;
//                                point2D.y = buildingFeature.geometry.points[index].y;
//                                point2DS.add(point2D);
//                                index++;
//                            }
//                            buildingGeometry.add(point2DS);
//                        }
//                    }
//                    List<ModelData> rooms = new ArrayList<>();
//                    for (Feature feature : floorFeatures) {
//                        if (feature == null)
//                            continue;
//                        if (feature.geometry == null)
//                            continue;
//
//                        HashMap<String, String> info = new HashMap<>();
//                        for (int i=0; i<feature.fieldNames.length; i++)
//                            info.put(feature.fieldNames[i], feature.fieldValues[i]);
//                        info.put("FLOORID", floorid);
//                        String key = String.format("%s.%s.%s", realBuildingId, floorid, info.get("SMID"));
//                        List<List<Point2D>> geometry = new ArrayList<>();
//                        int index = 0;
//                        for (int i=0; i<feature.geometry.parts.length; i++) {
//                            List<Point2D> point2DS = new ArrayList<>();
//                            for (int j=0; j<feature.geometry.parts[i]; j++) {
//                                Point2D point2D = new Point2D();
//                                point2D.x = feature.geometry.points[index].x;
//                                point2D.y = feature.geometry.points[index].y;
//                                point2DS.add(point2D);
//                                index++;
//                            }
//                            geometry.add(point2DS);
//                        }
//                        if (feature.geometry.type == GeometryType.REGION) {
//                            ModelData room = new ModelData(key, null, geometry, info);
//                            rooms.add(room);
//                        } else {
//                            ModelData room = new ModelData(key, geometry, null, info);
//                            rooms.add(room);
//                        }
//                    }
//                    IndoorMapData indoorMapData = new IndoorMapData(
//                            realBuildingId,
//                            floorid,
//                            buildingGeometry,
//                            buildingGeometry != null ? buildingFeature.geometry.getBounds() : null,
//                            rooms);
//                    Message msg = new Message();
//                    msg.obj = indoorMapData;
//                    msg.what = Common.QUERY_INDOOR_MAP;
//                    handler.sendMessage(msg);
//                } else
                    QueryUtils.queryIndoorMap(Common.parkId() + ":Buildings", realBuildingId, floorid, handler);
            }
        }
    }

    private void clearOpensMap() {
        currIndoorMap = "";
        openIndoors.clear();
        openBasements.clear();
        openedMaps.clear();
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, List<Overlay>> entry : namedOverlays.entrySet()) {
            if (entry.getKey().startsWith("indoor[building:")) {
                keys.add(entry.getKey());
                List<Overlay> ovls = entry.getValue();
                for (Overlay ov : ovls)
                    mapView.getOverlays().remove(ov);
            }
        }
        for (String key : keys)
            namedOverlays.remove(key);

        keys.clear();
        for (Map.Entry<String, List<Overlay>> entry : namedOverlays.entrySet()) {
            if (entry.getKey().startsWith("indoor[basement:")) {
                keys.add(entry.getKey());
                List<Overlay> ovls = entry.getValue();
                for (Overlay ov : ovls)
                    mapView.getOverlays().remove(ov);
            }
        }
        for (String key : keys)
            namedOverlays.remove(key);

        keys.clear();
        for (Map.Entry<String, List<Overlay>> entry : namedOverlays.entrySet()) {
            if (entry.getKey().startsWith("indoorStyle[")) {
                keys.add(entry.getKey());
                List<Overlay> ovls = entry.getValue();
                for (Overlay ov : ovls)
                    mapView.getOverlays().remove(ov);
            }
        }
        for (String key : keys)
            namedOverlays.remove(key);
    }

    public void setRoomStyle(String buildingId, String floorId, String key, String type, RoomStyle roomStyle) {
        boolean isUpdated = false;

        String indoorKey = String.format(indoorKeyTemplate, buildingId);
        String styleKey = String.format(indoorStyleKeyTemplate, buildingId, floorId, type);
        List<Overlay> ovls = null;
        if (namedOverlays.containsKey(styleKey)) {
            ovls = namedOverlays.get(styleKey);
            List<Overlay> old = new ArrayList<>();
            for (Overlay ov : ovls) {
                if (ov.getKey().startsWith(key)) {
                    mapView.getOverlays().remove(ov);
                    old.add(ov);
                    isUpdated = true;
                }
            }
            for (Overlay ov : old)
                ovls.remove(ov);
        }

        if (roomStyle != null && openIndoors.containsKey(indoorKey)) {
            IndoorMapData indoorMapData = openIndoors.get(indoorKey);
            for (ModelData modelData : indoorMapData.rooms) {
                String smId = null;
                String keyvalue = null;
                if (modelData.features.containsKey("SMID"))
                    smId = modelData.features.get("SMID");
                if (modelData.features.containsKey(type))
                    keyvalue = modelData.features.get(type);
                if (keyvalue != null && keyvalue.equalsIgnoreCase(key)) {
                    if (ovls == null) {
                        ovls = new ArrayList<>();
                        namedOverlays.put(styleKey, ovls);
                    }
                    if (modelData.geometry != null) {
                        Paint paint = new Paint();
                        paint.setAntiAlias(true);
                        paint.setStyle(Paint.Style.FILL_AND_STROKE);
                        paint.setColor(roomStyle.fillColor);
                        paint.setAlpha(roomStyle.fillOpacity);

                        for (List<Point2D> points : modelData.geometry) {
                            PolygonOverlay ov = new PolygonOverlay(paint);
                            ov.setShowPoints(false);
                            ov.setData(points);
                            ov.setKey(keyvalue + smId);
                            ov.setZIndex(indoorZIndex);
                            ovls.add(ov);
                            mapView.getOverlays().add(ov);
                        }

                        double minX = 180, minY = 90;
                        double maxX = -180 ,maxY = -90;
                        for (List<Point2D> points : modelData.geometry) {
                            for (Point2D point : points) {
                                if (minX >= point.x) {
                                    minX = point.x;
                                }
                                if (minY >= point.y) {
                                    minY = point.y;
                                }
                                if (maxX <= point.x) {
                                    maxX = point.x;
                                }
                                if (maxY <= point.y) {
                                    maxY = point.y;
                                }
                            }

                            Point2D point2D = new Point2D((minX+maxX)/2, (minY+maxY)/2);
                            String roomName = roomStyle.isShowText ? modelData.features.get("NAME") : "";
                            TextPolygonOverlay ov = new TextPolygonOverlay(new double[] {point2D.y, point2D.x}, roomName, roomStyle.textColor);
                            ov.setShowPoints(false);
                            ov.setData(points);
                            ov.setKey(keyvalue + smId);
                            ov.setZIndex(indoorZIndex);
                            ovls.add(ov);
                            mapView.getOverlays().add(ov);
                        }
                    } else {
                        Paint paint = new Paint();
                        paint.setAntiAlias(true);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setColor(roomStyle.lineColor);
                        paint.setAlpha(roomStyle.lineOpacity);
                        paint.setStrokeWidth(roomStyle.lineWidth);

                        for (List<Point2D> points : modelData.outline) {
                            LineOverlay ov = new LineOverlay(paint);
                            ov.setShowPoints(false);
                            ov.setData(points);
                            ov.setKey(keyvalue + smId);
                            ovls.add(ov);
                            mapView.getOverlays().add(ov);
                        }
                    }
                    isUpdated = true;
                }
            }
        }

        if (isUpdated)
            mapView.invalidate();
    }

    public void setRoomStyle(String buildingId, String floorId, String roomId, RoomStyle roomStyle) {
        setRoomStyle(buildingId, floorId, roomId, "ROOMID", roomStyle);
    }

    /**
     * 切换到室外地图
     */
    public void switchOutdoor() {
        showIndoorMap("", "");
    }

    /**
     * 绘制线
     * @param points
     */
    public void drawCustomPath(RoutePoint[] points) {
        List<Point2D> point2DS = new ArrayList<>();
        for (RoutePoint p : points) {
            Point2D point2D = new Point2D(p.coords[1], p.coords[0]);
            point2DS.add(point2D);
        }
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(points[0].color);
        paint.setAlpha(points[0].opacity);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(points[0].width);
        LineOverlay ovly = new LineOverlay(paint);
        ovly.setLinePaint(paint);
        ovly.setPointPaint(paint);
        ovly.setShowPoints(false);
        ovly.setData(point2DS);
        routeOverlay.add(ovly);
        mapView.getOverlays().add(ovly);
        mapView.invalidate();
    }

    /**
     * 获取路径规划数据
     * @param start
     * @param end
     */
    public void getPathPlanData(RoutePoint start, RoutePoint end, PathPlanDataListener listener) {
        PathPlanDataUtil.excutePathService(mapView, start, end, listener);
    }

    /**
     * 获取路径规划数据回调
     * @param listener
     */
    public void getPathPlanDataListener(PathPlanDataListener listener) {
        pathPlanDataListener = listener;
    }

    /**
     * 清除获取路径规划数据回调
     */
    public void removePathPlanDataListener() {
        pathPlanDataListener = null;
    }

    public void calcRoutePath(RoutePoint start, RoutePoint end, RoutePoint[] way) {
        PresentationStyle ps = new PresentationStyle();
        ps.lineWidth = 20;
        ps.fillColor = Color.parseColor("#0216F2");
        ps.opacity = 120;
        NetWorkAnalystUtil.excutePathService(mapView, start, end, way, ps, handler);
    }

    /**
     * 路径规划
     * @param start
     * @param end
     * @param way
     * @param ps
     */
    public void calcRoutePath(RoutePoint start, RoutePoint end, RoutePoint[] way, PresentationStyle ps) {
        NetWorkAnalystUtil.excutePathService(mapView, start, end, way, ps, handler);
    }

    /**
     * 路径规划回调
     * @param listener
     */
    public void addRouteListener(CalculateRouteListener listener) {
        mCalculateRouteListener = listener;
    }

    /**
     * 清除路径规划回调
     */
    public void removeRouteListener() {
        mCalculateRouteListener = null;
    }

    public void setRouteFacility(String[] keys, GeneralMarker[] markers) {
        if (keys == null || markers == null)
            return;

        int len = Math.min(keys.length, markers.length);
        for (int i=0; i<len; i++)
            defaultFacilities.put(keys[i], markers[i]);
    }

    /**
     * 清除路径轨迹
     */
    public void clearPath() {
        calculatedRoute = null;
        if (routeOverlay != null && routeOverlay.size() > 0) {
            for (LineOverlay ovly : routeOverlay) {
                mapView.getOverlays().remove(ovly);
                ovly.setData(new ArrayList<Point2D>());
            }
            routeOverlay.clear();
        }

        if (namedOverlays.containsKey(calculatdRouteKey)) {
            List<Overlay> ovls = namedOverlays.get(calculatdRouteKey);
            for (Overlay ov: ovls)
                mapView.getOverlays().remove(ov);
            namedOverlays.remove(calculatdRouteKey);
        }
        mapView.invalidate();
    }

    public boolean isInRoute(double lat, double lng, double delta) {
        if (calculatedRoute != null) {
            double deltaLng = (delta / 1000) * 20 / 104;
            if (lat >= 20 && lat < 26)
                deltaLng = (delta / 1000) * 26 / 100;
            else if (lat >= 26 && lat < 30)
                deltaLng = (delta / 1000) * 30 / 100;
            else if (lat >= 30 && lat < 36)
                deltaLng = (delta / 1000) * 36 / 100;
            else if (lat >= 36 && lat < 40)
                deltaLng = (delta / 1000) * 40 / 100;
            else if (lat >= 40 && lat < 44)
                deltaLng = (delta / 1000) * 44 / 100;
            else if (lat >= 44)
                deltaLng = (delta / 1000) * 51 / 100;
            double deltaLat = (delta / 1000) * 1 / 111;
            for (com.supermap.services.components.commontypes.Path[] paths : calculatedRoute.route) {
                if (paths == null)
                    continue;
                for (com.supermap.services.components.commontypes.Path path : paths) {
                    com.supermap.services.components.commontypes.Point2D[] route = path.route.points;
                    if (route.length == 1) {
                        if (Math.abs(route[0].x - lng) <= deltaLng && Math.abs(route[0].y - lat) <= deltaLat)
                            return true;
                        else
                            continue;
                    }
                    for (int i = 0; i < route.length - 1; i++) {
                        boolean isInSection = false;
                        if (route[i].y + deltaLat > lat && route[i + 1].y - deltaLat < lat && route[i].x + deltaLng > lng && route[i + 1].x - deltaLng < lng)
                            isInSection = true;
                        else if (route[i].y + deltaLat > lat && route[i + 1].y - deltaLat < lat && route[i + 1].x + deltaLng > lng && route[i].x - deltaLng < lng)
                            isInSection = true;
                        else if (route[i + 1].y + deltaLat > lat && route[i].y - deltaLat < lat && route[i].x + deltaLng > lng && route[i + 1].x - deltaLng < lng)
                            isInSection = true;
                        else if (route[i + 1].y + deltaLat > lat && route[i].y - deltaLat < lat && route[i + 1].x + deltaLng > lng && route[i].x - deltaLng < lng)
                            isInSection = true;
                        if (isInSection) {
                            double x1 = route[i].x;
                            double y1 = route[i].y;
                            double x2 = route[i + 1].x;
                            double y2 = route[i + 1].y;
                            double A = (y1 - y2) / (x1 - x2);
                            double B = -1;
                            double C = y1 - A * x1;
                            double d = Math.abs((A * lng + B * lat + C) / (Math.sqrt(A * A + B * B)));
                            return (d <= deltaLat) && (d <= deltaLng);
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * 绘制热力图
     * @param points 热力点个数
     * @param radius 半径
     * @param opacity 透明度
     */
    public void showHeatMap(HeatPoint[] points, int radius, double opacity) {
        HeatmapDrawable heatmapDrawable = new HeatmapDrawable(points, radius, handler);
        heatmapList.add(heatmapDrawable);
        Point leftTop = new Point();
        Point rightBottom = new Point();
        mapView.getProjection().toPixels(new Point2D(heatmapDrawable.geoLeft, heatmapDrawable.geoTop), leftTop);
        mapView.getProjection().toPixels(new Point2D(heatmapDrawable.geoRight, heatmapDrawable.geoBottom), rightBottom);

        DrawableOverlay overlay = new DrawableOverlay(heatmapDrawable,
                new BoundingBox(new Point2D(heatmapDrawable.geoLeft, heatmapDrawable.geoTop),
                        new Point2D(heatmapDrawable.geoRight, heatmapDrawable.geoBottom)));
        mapView.getOverlays().add(overlay);
        heatmapDrawable.fitToMapView(leftTop.x, leftTop.y, rightBottom.x, rightBottom.y, true);
    }

    /**
     * 清除热力图
     */
    public void clearHeatMap() {
        List<Overlay> ovlys = mapView.getOverlays();
        List<Overlay> ovdels = new ArrayList<>();
        if (ovlys != null && ovlys.size() > 0) {
            for (Overlay ov : ovlys) {
                if (ov instanceof DrawableOverlay)
                    ovdels.add(ov);
            }
            for (Overlay ov : ovdels)
                mapView.getOverlays().remove(ov);
            mapView.invalidate();
        }
        heatmapList.clear();
    }

    public void displayPerimeter(String parkId, String normalColor, int normalWidth, int normalOpacity,
                                 String alarmColor, int alarmWidth, int alarmOpacity, int[] alarmList) {
        PerimeterStyle alarm = new PerimeterStyle(Color.parseColor(alarmColor), alarmWidth, alarmOpacity);
        PerimeterStyle normal = new PerimeterStyle(Color.parseColor(normalColor), normalWidth, normalOpacity);
        displayPerimeter(parkId, alarm, normal, alarmList);
    }

    public void displayPerimeter(String parkId, PerimeterStyle alarm, PerimeterStyle normal, int[] alarmList) {
        QueryUtils.queryPerimeter(parkId, alarm, normal, alarmList, handler);
    }

    /**
     * 清除边界线
     */
    public void removePerimeter() {
        if (namedOverlays.containsKey(perimeterKey)) {
            List<Overlay> ovls = namedOverlays.get(perimeterKey);
            for (Overlay ov: ovls)
                mapView.getOverlays().remove(ov);
            namedOverlays.remove(perimeterKey);
            mapView.invalidate();
        }
    }

    /**
     * 模型高亮
     * @param buildingId
     * @param modId 车位编号
     */
    public void showModelHighlight(String buildingId, String floorid, String[] modId) {
        List<String[]> ids = new ArrayList<>();
        ids.add(modId);

        //高亮样式
        List<PresentationStyle> pss = new ArrayList<>();
        PresentationStyle hps = new PresentationStyle();
        hps.lineWidth = 5;
        hps.fillColor = Color.GREEN;
        hps.opacity = 150;
        pss.add(hps);

        //默认样式
        PresentationStyle normal = new PresentationStyle();
        normal.lineWidth = 5;
        normal.fillColor = Color.parseColor("#2B94BF");
        normal.opacity = 150;

        showModelHighlight(ids, buildingId, floorid, pss, normal);
    }

    /**
     * 模型高亮
     * @param modIds
     * @param pss
     * @param normal
     */
    public void showModelHighlight(List<String[]> modIds, String buildingId, String floorid, List<PresentationStyle> pss, PresentationStyle normal) {
        isShowHighLight = true;
        String realBuildingId = buildingId;
        BuildingConvertMappingData data = GisDataCache.getInstance(getContext(), this.mMapCacheListener).getBuidingConver(buildingId, floorid);
        if (data != null) {
            realBuildingId = data.targetId;
        }
        QueryUtils.queryModel(modIds, realBuildingId, floorid, pss, normal, handler);
    }

    /**
     * 清除模型高亮
     */
    public void removeModelhighlighting() {
        isShowHighLight = false;
        if (namedOverlays.containsKey(modelsKey)) {
            List<Overlay> ovls = namedOverlays.get(modelsKey);
            for (Overlay ov: ovls)
                mapView.getOverlays().remove(ov);
            ovls.clear();
            namedOverlays.remove(modelsKey);
            mapView.invalidate();
        }
    }

    /**
     * 查询信息
     * @param parkId
     * @param address
     * @param callback
     */
    public void queryObject(final String parkId, final String address, final QueryCallback callback) {
        Common.fixedThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                String addr = null;
                double lng = 0, lat = 0;
                try {
                    String ret = "[]";
                    try {
                        String enc_addr = URLEncoder.encode(address, "UTF-8");
                        String url = String.format("%s%s?address=%s&fromIndex=0&toIndex=10&maxReturn=1", Common.getHost(), Common.GEO_CODE_URL(), enc_addr);
                        if (!TextUtils.isEmpty(Common.ROMA_KEY)) {
                            url += "&" + Common.ROMA_KEY;
                        }
                        if (logEnable) {
                            Log.e(TAG + "queryObject", url);
                        }
                        ret = getStringFromURL(url);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    JSONArray arr = new JSONArray();
                    try {
                        arr = new JSONArray(ret);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    if (arr.length() > 0) {
                        try {
                            JSONObject obj = arr.getJSONObject(0);
                            addr = obj.getString("address");
                            lng = obj.getJSONObject("location").getDouble("x");
                            lat = obj.getJSONObject("location").getDouble("y");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (!TextUtils.isEmpty(addr)) {
                    ObjectInfo info = QueryUtils.getObjectInfo(parkId, lat, lng);
                    if (info != null) {
                        info.address = addr;
                        if (!TextUtils.isEmpty(info.address) && info.address.contains(",")) {
                            try {
                                String[] array = info.address.split(",");
                                info.parkId = array[0];
                                info.parkX = array[1];
                                info.parkY = array[2];
                                info.roomCode = array[3];
                                info.zoneX = array[4];
                                info.zoneY = array[5];
                                info.cnName = array[6];
                                info.enName = array[7];
                            } catch (ArrayIndexOutOfBoundsException e) {
                                e.printStackTrace();
                            }
                        }
                        info.lng = lng;
                        info.lat = lat;
                        if (callback != null)
                            callback.onQueryFinished(info);
                    }
                }
            }
        });
    }

    /**
     * 获取building信息
     * @param parkId
     * @param buildingId
     * @param callback
     */
    public void getBuldingInfo(final String parkId, final String buildingId, final QueryCallback callback) {
        Common.fixedThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                ObjectInfo info = QueryUtils.getBuildingInfo(parkId, buildingId);
                if (info != null) {
                    try {
                        String ret = "[]";
                        try {
                            String enc_addr = URLEncoder.encode(info.getStrParam("NAME"), "UTF-8");
                            String url = String.format("%s%s?address=%s&fromIndex=0&toIndex=10&maxReturn=1", Common.getHost(), Common.GEO_CODE_URL(), enc_addr);
                            if (!TextUtils.isEmpty(Common.ROMA_KEY)) {
                                url += "&" + Common.ROMA_KEY;
                            }
                            if (logEnable) {
                                Log.e(TAG + "getBuldingInfo", url);
                            }
                            ret = getStringFromURL(url);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        JSONArray arr = new JSONArray();
                        try {
                            arr = new JSONArray(ret);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        if (arr.length() > 0) {
                            try {
                                JSONObject obj = arr.getJSONObject(0);
                                info.address = obj.getString("address");
                                if (!TextUtils.isEmpty(info.address) && info.address.contains(",")) {
                                    try {
                                        String[] array = info.address.split(",");
                                        info.parkId = array[0];
                                        info.parkX = array[1];
                                        info.parkY = array[2];
                                        info.roomCode = array[3];
                                        info.zoneX = array[4];
                                        info.zoneY = array[5];
                                        info.cnName = array[6];
                                        info.enName = array[7];
                                    } catch (ArrayIndexOutOfBoundsException e) {
                                        e.printStackTrace();
                                    }
                                }
                                info.lng = obj.getJSONObject("location").getDouble("x");
                                info.lat = obj.getJSONObject("location").getDouble("y");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (callback != null)
                        callback.onQueryFinished(info);
                }
            }
        });
    }

    /**
     * 查询数据
     * @param dataset 查询条件
     * @param filter 过滤条件
     * @return
     */
    public List<ObjectInfo> query(String dataset, String filter) {
        return QueryUtils.getObjects(dataset, filter);
    }

    public void addBuildingListener(BuildingListener listener) {
        if (!mBuildingListener.contains(listener))
            mBuildingListener.add(listener);
    }

    public void removeBuildingListener(BuildingListener listener) {
        mBuildingListener.remove(listener);
        List<Overlay> overlays = mapView.getOverlays();
        for (Overlay ov : overlays) {
            String key = ov.getKey();
            if (!TextUtils.isEmpty(key) && key.startsWith("building:")) {
                PolygonOverlay unselect = (PolygonOverlay) ov;
                if (unselect != null)
                    unselect.setLinePaint(getBuildingSelectPaint(false));
            }
        }
        mapView.invalidate();
    }

    public void addModelListener(ModelListener listener) {
        if (!mModelListener.contains(listener))
            mModelListener.add(listener);
    }

    public void removeModelListener(ModelListener listener) {
        mModelListener.remove(listener);
    }

    public void addZoomListener(ZoomListener listener) {
        if (!mZoomListener.contains(listener))
            mZoomListener.add(listener);
    }

    public void removeZoomListener(ZoomListener listener) {
        mZoomListener.remove(listener);
    }

    public void addMapListener(MapListener listener) {
        if (!mMapListener.contains(listener))
            mMapListener.add(listener);
    }

    public void removeMapListener(MapListener listener) {
        mMapListener.remove(listener);
    }

    private String _lang = "cn";
    public void setLanguage(String lang){
        if(lang.equals("cn"))
            this._lang = "cn";
        else if(lang.equals("en"))
            this._lang = "en";
        else{
            this._lang = "cn";
        }
    }

    /**
     * 设置地图隐藏级别，隐藏对应级别图层
     * @param level
     */
    public void setHideLevel(int level) {
        mHideLevel = level;
        switchMarkerHide();
    }

    private void switchMarkerHide() {
        boolean show = mapView.getZoomLevel() > mHideLevel;
        List<Overlay> overlays = mapView.getOverlays();
        for (Overlay ov : overlays) {
            if (ov instanceof DefaultItemizedOverlay) {
                DefaultItemizedOverlay overlay = (DefaultItemizedOverlay) ov;
                for (int i = 0, cnt = overlay.size(); i < cnt; i++) {
                    Drawable d = overlay.getItem(i).getMarker(0);
                    d.setVisible(show, true);
                    d.invalidateSelf();
                }
            }
        }
        mapView.invalidate();
    }

    public static void addLocateListener(LocationListener listener) {
        if (!mPosListener.contains(listener))
            mPosListener.add(listener);
    }

    public static void removeLocateListener(LocationListener listener) {
        mPosListener.remove(listener);
    }

    /**
     * 开始定位
     */
    public void startLocate() {
        if (_instance != null) {
            if (_instance.mLocationClient != null) {
                _instance.mLocationClient.stopLocation();
                _instance.mLocationClient.startLocation();
            }
        }
        isLocatorRunning = true;
    }

    /**
     * 停止定位
     */
    public void stopLocate() {
        isLocatorRunning = false;
        if (_instance != null) {
            if (_instance.mLocationClient != null)
                _instance.mLocationClient.stopLocation();
        }
    }

    /**
     * 加载ivas
     * @param test true获取测试数据，false获取正式数据
     */
    public static void setLocDecoder(boolean test, IVASMappingListener callback) {
        GisDataCache.initIVASMapping(test, callback);
    }

    /**
     * 查询对应楼层ivas数据集
     * @param id
     * @param floor
     * @return
     */
    public ExternLocData decodeLocLocation(String id, int floor) {
        IVASMappingData data = GisDataCache.getInstance(getContext(), this.mMapCacheListener).getIVASBuilding(id);
        if (data != null && data.ivasFloorList != null && data.floorList != null) {
            for (int i=0; i<data.ivasFloorList.length && i<data.floorList.length; i++) {
                if (TextUtils.equals(data.ivasFloorList[i], floor+"")) {
                    return new ExternLocData(data.lat, data.lng, data.buildingId, data.floorList[i], data.roomCode, data.fields, data.values);
                }
            }
        }
        return null;
    }

    static class ExecuteFinished extends Handler {
        private GisView host;
        ExecuteFinished(GisView host) {
            this.host = host;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Common.ADD_GENERAL_MARKER:
                    UrlMarkerMaker.UrlGeneralMarker gmarker = (UrlMarkerMaker.UrlGeneralMarker) msg.obj;
                    host.addMarker(gmarker.layerId, gmarker.layerIndex, gmarker.markers);
                    break;
                case Common.ADD_FLASH_MARKER:
                    UrlMarkerMaker.UrlFlashMarker fmarker = (UrlMarkerMaker.UrlFlashMarker) msg.obj;
                    host.addMarker(fmarker.layerId, fmarker.layerIndex, fmarker.markers);
                    break;
                case Common.QUERY_BUILDINGS:
                    List<QueryUtils.BuildingResult> buildings = (List<QueryUtils.BuildingResult>) msg.obj;
                    GisDataCache.getInstance(host.getContext(), host.mMapCacheListener).initIndoorMaps(buildings);
                    host.initBuildingEvent(buildings);
                    break;
                case Common.QUERY_INDOOR_MAP:
                    IndoorMapData indoor = (IndoorMapData) msg.obj;
                    String indoorKey = String.format(indoorKeyTemplate, indoor.buildingId);
                    if (!host.openIndoors.containsKey(indoorKey))
                        host.openIndoors.put(indoorKey, indoor);
                    host.renderIndoorMap(indoor);
                    break;
                case Common.QUERY_BASEMENT_MAP:
                    QueryUtils.BasementMapResult basement = (QueryUtils.BasementMapResult) msg.obj;
                    String basementKey = String.format(basementKeyTemplate, basement.floorId);
                    if (!host.openBasements.containsKey(basementKey))
                        host.openBasements.put(basementKey, basement);
                    host.renderBasementMap(basement);
                    break;
                case Common.QUERY_PERIMETER:
                    QueryUtils.PerimeterResult perimeter = (QueryUtils.PerimeterResult) msg.obj;
                    host.renderPerimeter(perimeter);
                    break;
                case Common.QUERY_MODEL:
                    QueryUtils.ModelResult model = (QueryUtils.ModelResult) msg.obj;
                    host.renderModel(model);
                    break;
                case Common.PATH_PLAN_DATA:
                    List<Point2D> point2DS = (ArrayList<Point2D>) msg.obj;
                    if (host.pathPlanDataListener != null) {
                        if (point2DS != null && point2DS.size() > 0) {
                            host.pathPlanDataListener.pathPlanDataSuccess(point2DS);
                        } else {
                            host.pathPlanDataListener.pathPlanDataFailed("查询失败");
                        }
                    }
                    break;
                case Common.ANALYST_ROUTE:
                    NetWorkAnalystUtil.CalculatedRoute route = (NetWorkAnalystUtil.CalculatedRoute) msg.obj;
                    if (route == null) {
                        if (host.mCalculateRouteListener != null)
                            host.mCalculateRouteListener.calculateRouteEvent(new RouteEvent(false, 0));
                    } else if (route.route == null) {
                        if (host.mCalculateRouteListener != null)
                            host.mCalculateRouteListener.calculateRouteEvent(new RouteEvent(false, 0));
                    } else {
                        double pathLength = 0;
                        int failedCount = 0;
                        for (com.supermap.services.components.commontypes.Path[] paths : route.route) {
                            if (paths == null) {
                                failedCount++;
                                continue;
                            }
                            for (com.supermap.services.components.commontypes.Path path : paths) {
                                if (path == null) {
                                    failedCount++;
                                    continue;
                                }
                                pathLength += path.route.length;
                            }
                        }
                        host.renderCalculatedRoute(route);
                        if (failedCount > 0) {
                            if (host.mCalculateRouteListener != null)
                                host.mCalculateRouteListener.calculateRouteEvent(new RouteEvent(false, pathLength));
                        } else {
                            if (host.mCalculateRouteListener != null)
                                host.mCalculateRouteListener.calculateRouteEvent(new RouteEvent(true, pathLength));
                        }
                    }
                    break;
                case Common.HEAT_MAP_CALC_END:
                    host.mapView.invalidate();
                case Common.EVENT_MAP_TAP:
                    if (host.mMapListener.size() > 0) {
                        for (MapListener listener : host.mMapListener)
                            listener.mapEvent((MapEvent) msg.obj);
                    }
                    break;
                case Common.START_TIMER:
                    host.handler.removeMessages(Common.START_TIMER);
                    MapController controller = host.mapView.getController();
                    if (host.mapView.getZoomLevel() > host.maxZoomLevel)
                        controller.setZoom(host.maxZoomLevel);
                    host.fitMapWithNoBlank(host.mapView);
                    if (host.isMapLoaded == false && host.mMapLoadedListener != null) {
                        if (host.mapView != null) {
                            double[] bounds = new double[4];
                            BoundingBox bx = host.mapView.getViewBounds();
                            bounds[0] = bx.getTop();
                            bounds [1] = bx.getLeft();
                            bounds[2] = bx.getBottom();
                            bounds[3] = bx.getRight();
                            if (bounds[0] * bounds[1] * bounds[2] * bounds[3] > 0.000000000001) {
                                host.isMapLoaded = true;
                                Point2D point2D = host.mapView.getCenter();
                                host.mMapLoadedListener.OnMapLoaded(new MapLoadedEvent(Common.parkId(), bounds, point2D.y, point2D.x));
                            }
                        }
                    }
                    host.handler.sendEmptyMessageDelayed(0, 100);
                    break;
                case Common.STOP_TIMER:
                    host.handler.removeMessages(Common.START_TIMER);
                    break;
                default:
                    break;
            }
        }
    }

    private void initBuildingEvent(List<QueryUtils.BuildingResult> buildings) {
        this.buildings = buildings;

        List<Overlay> ovls;
        if (namedOverlays.containsKey(buildingTouchKey)) {
            ovls = namedOverlays.get(buildingTouchKey);
            for (Overlay ov : ovls)
                mapView.getOverlays().remove(ov);
            ovls.clear();
        } else {
            ovls = new ArrayList<>();
        }

        if (buildings != null) {
            for (QueryUtils.BuildingResult br : buildings) {
                PolygonOverlay building = new PolygonOverlay(getBuildingSelectPaint(false));
                building.setData(br.buildingGeometry);
                building.setShowPoints(false);
                building.setTapListener(this);
                building.setKey(String.format("building:[%d]", br.feature.getID()));
                ovls.add(building);
                mapView.getOverlays().add(building);
            }
        }
        mapView.invalidate();
    }

    /**
     * 渲染室内地图
     * @param indoor
     */
    private void renderIndoorMap(IndoorMapData indoor) {
        String indoorKey = String.format(indoorKeyTemplate, indoor.buildingId);
        List<Overlay> ovls = null;
        if (namedOverlays.containsKey(indoorKey)) {
            ovls = namedOverlays.get(indoorKey);
        }

        Canvas canvas = null;
        Bitmap baseBmp = null;
        Path pathBuild = null;
        Path path = null;
        Path pathLine = null;
        double r = 0;
        Point2D leftTop = new Point2D();
        Point2D rightBottom = new Point2D();
        if (indoor.rooms != null && indoor.rooms.size() > 0) {
            int dw = mapView.getWidth();
            int dh = mapView.getHeight();
            leftTop = mapView.toMapPoint(new Point(0, 0));
            rightBottom = mapView.toMapPoint(new Point(dw, dh));
            if (leftTop == null || rightBottom == null)
                return;
            r = (double) dw / (rightBottom.x - leftTop.x);
//            Log.i("--indoor", String.format("%d, %d; base: %f, %f", dw, dh, leftTop.x, leftTop.y));
            if (dw > 10 && dh > 10)
                baseBmp = Bitmap.createBitmap((int) dw, (int) dh, Bitmap.Config.ARGB_8888);
            if (baseBmp != null) {
                canvas = new Canvas(baseBmp);
                canvas.drawColor(Color.TRANSPARENT);
                path = new Path();
                pathLine = new Path();
            }
        }

        if (indoor.buildingGeometry != null && indoor.buildingGeometry.size() > 0) {
            pathBuild = new Path();
            for (List<Point2D> obj : indoor.buildingGeometry) {
                double x, y;
                x = (obj.get(0).x - leftTop.x) * r;
                y = (leftTop.y - obj.get(0).y) * r;
                pathBuild.moveTo((float) x, (float) y);
                for (int i=1; i<obj.size(); i++) {
                    x = (obj.get(i).x - leftTop.x) * r;
                    y = (leftTop.y - obj.get(i).y) * r;
                    pathBuild.lineTo((float) x, (float) y);
                }
            }
        }

        if (indoor.rooms != null && indoor.rooms.size() > 0) {
            Path combBuild = new Path();
            for (ModelData roomData : indoor.rooms) {
                double x, y;
                if (roomData.geometry != null) {
                    for (List<Point2D> obj : roomData.geometry) {
                        x = (obj.get(0).x - leftTop.x) * r;
                        y = (leftTop.y - obj.get(0).y) * r;
                        path.moveTo((float) x, (float) y);
                        if (pathBuild == null)
                            combBuild.moveTo((float) x, (float) y);
                        for (int i=1; i<obj.size(); i++) {
                            x = (obj.get(i).x - leftTop.x) * r;
                            y = (leftTop.y - obj.get(i).y) * r;
                            path.lineTo((float) x, (float) y);
                            if (pathBuild == null)
                                combBuild.lineTo((float) x, (float) y);
                        }
                    }
                }
                if (roomData.outline != null) {
                    for (List<Point2D> obj : roomData.outline) {
                        x = (obj.get(0).x - leftTop.x) * r;
                        y = (leftTop.y - obj.get(0).y) * r;
                        path.moveTo((float) x, (float) y);
                        for (int i=1; i<obj.size(); i++) {
                            x = (obj.get(i).x - leftTop.x) * r;
                            y = (leftTop.y - obj.get(i).y) * r;
                            path.lineTo((float) x, (float) y);
                        }
                    }
                }
            }
            if (pathBuild == null)
                pathBuild = combBuild;
        }
        if (canvas != null) {
            openedMaps.add(String.format("%s:%s", indoor.buildingId, indoor.floorId));
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.parseColor("#D8D8D8"));
            paint.setAlpha(255);
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeWidth(1);
            canvas.drawPath(pathBuild, paint);

            Paint linepaint = new Paint();
            linepaint.setAntiAlias(true);
            linepaint.setColor(Color.parseColor("#000000"));
            linepaint.setAlpha(128);
            linepaint.setStyle(Paint.Style.STROKE);
            linepaint.setStrokeWidth(1f);
            canvas.drawPath(pathLine, linepaint);

            Paint rgnpaint = new Paint();
            rgnpaint.setAntiAlias(true);
            rgnpaint.setColor(Color.parseColor("#000000"));
            rgnpaint.setAlpha(255);
            rgnpaint.setStyle(Paint.Style.STROKE);
            rgnpaint.setStrokeWidth(1f);
            canvas.drawPath(path, rgnpaint);

            if (ovls == null) {
                ovls = new ArrayList<>();
                namedOverlays.put(indoorKey, ovls);
            }
            boolean needNewOverlay = true;
            for (Overlay overlay : ovls) {
                if (overlay instanceof DrawableOverlay) {
                    needNewOverlay = false;
                    break;
                }
            }
            if (needNewOverlay) {
                DrawableOverlay floorOverlay = new DrawableOverlay();
                floorOverlay.setDrawable(new BitmapDrawable(getResources(), baseBmp), new BoundingBox(leftTop, rightBottom));
                floorOverlay.setZIndex(indoorZIndex);
                ovls.add(floorOverlay);
                mapView.getOverlays().add(floorOverlay);
            } else {
                for (Overlay overlay : ovls) {
                    if (overlay instanceof DrawableOverlay) {
                        DrawableOverlay floorOverlay = (DrawableOverlay) overlay;
                        floorOverlay.setDrawable(new BitmapDrawable(getResources(), baseBmp), new BoundingBox(leftTop, rightBottom));
                        break;
                    }
                }
            }
        }
        mapView.invalidate();
        renderCalculatedRoute(calculatedRoute);
        if (indoorCallback != null) {
            indoorCallback.done();
            indoorCallback = null;
        }
    }

    /**
     * 绘制地下室
     * @param basement
     */
    private void renderBasementMap(QueryUtils.BasementMapResult basement) {
        String basementKey = String.format(basementKeyTemplate, basement.floorId);
        List<Overlay> ovls = null;
        if (namedOverlays.containsKey(basementKey)) {
            ovls = namedOverlays.get(basementKey);
        }

        Canvas canvas = null;
        Bitmap baseBmp = null;
        Path pathStruct = null;
        Path path = null;
        double r = 0;
        Point2D leftTop = new Point2D();
        Point2D rightBottom = new Point2D();
        if ((basement.structureGeometry != null && basement.structureGeometry.size() > 0)
                ||(basement.floorGeometry != null && basement.floorGeometry.size() > 0)) {
            int dw = mapView.getWidth();
            int dh = mapView.getHeight();
            leftTop = mapView.toMapPoint(new Point(0, 0));
            rightBottom = mapView.toMapPoint(new Point(dw, dh));
            if (leftTop == null || rightBottom == null)
                return;
            r = (double) dw / (rightBottom.x - leftTop.x);
//            Log.i("--basement", String.format("%d, %d; base: %f, %f", dw, dh, leftTop.x, leftTop.y));
            if (dw > 10 && dh > 10)
                baseBmp = Bitmap.createBitmap((int) dw, (int) dh, Bitmap.Config.ARGB_8888);
            if (baseBmp != null) {
                canvas = new Canvas(baseBmp);
                canvas.drawColor(Color.TRANSPARENT);
                pathStruct = new Path();
                path = new Path();
            }
        }

        if (basement.structureGeometry != null && basement.structureGeometry.size() > 0) {
            for (List<Point2D> obj : basement.structureGeometry) {
                double x, y;
                x = (obj.get(0).x - leftTop.x) * r;
                y = (leftTop.y - obj.get(0).y) * r;
                pathStruct.moveTo((float) x, (float) y);
                for (int i=1; i<obj.size(); i++) {
                    x = (obj.get(i).x - leftTop.x) * r;
                    y = (leftTop.y - obj.get(i).y) * r;
                    pathStruct.lineTo((float) x, (float) y);
                }
            }
        }

        if (basement.floorGeometry != null && basement.floorGeometry.size() > 0) {
            for (List<Point2D> obj : basement.floorGeometry) {
                double x, y;
                x = (obj.get(0).x - leftTop.x) * r;
                y = (leftTop.y - obj.get(0).y) * r;
                path.moveTo((float) x, (float) y);
                for (int i=1; i<obj.size(); i++) {
                    x = (obj.get(i).x - leftTop.x) * r;
                    y = (leftTop.y - obj.get(i).y) * r;
                    path.lineTo((float) x, (float) y);
                }
            }
        }
        if (canvas != null) {
            String opnd = String.format("Basement:%s", basement.floorId);
            if (!openedMaps.contains(opnd))
                openedMaps.add(opnd);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.parseColor("#2F4F4F"));
            paint.setAlpha(255);
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeWidth(1f);
            canvas.drawPath(pathStruct, paint);
            paint.setColor(Color.parseColor("#5CE7FF"));
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawPath(pathStruct, paint);

            paint.setColor(Color.parseColor("#2F4F4F"));
            paint.setAlpha(255);
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeWidth(1f);
            canvas.drawPath(path, paint);
            paint.setColor(Color.parseColor("#5CE7FF"));
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawPath(path, paint);

            if (ovls == null) {
                ovls = new ArrayList<>();
                namedOverlays.put(basementKey, ovls);
            }
            boolean needNewOverlay = true;
            for (Overlay overlay : ovls) {
                if (overlay instanceof DrawableOverlay) {
                    needNewOverlay = false;
                    break;
                }
            }
            if (needNewOverlay) {
                DrawableOverlay floorOverlay = new DrawableOverlay();
                floorOverlay.setZIndex(parkingZIndex);
                floorOverlay.setDrawable(new BitmapDrawable(getResources(), baseBmp), new BoundingBox(leftTop, rightBottom));
                ovls.add(floorOverlay);
                mapView.getOverlays().add(floorOverlay);
            } else {
                for (Overlay overlay : ovls) {
                    if (overlay instanceof DrawableOverlay) {
                        DrawableOverlay floorOverlay = (DrawableOverlay) overlay;
                        floorOverlay.setZIndex(parkingZIndex);
                        floorOverlay.setDrawable(new BitmapDrawable(getResources(), baseBmp), new BoundingBox(leftTop, rightBottom));
                        break;
                    }
                }
            }
        }
        mapView.invalidate();
        renderCalculatedRoute(calculatedRoute);
        if (indoorCallback != null) {
            indoorCallback.done();
            indoorCallback = null;
        }
    }

    /**
     * 绘制路径规划路线
     * @param route
     */
    private void renderCalculatedRoute(NetWorkAnalystUtil.CalculatedRoute route) {
        List<Overlay> ovls;
        if (namedOverlays.containsKey(calculatdRouteKey)) {
            ovls = namedOverlays.get(calculatdRouteKey);
            for (Overlay ov: ovls)
                mapView.getOverlays().remove(ov);
            ovls.clear();
        } else {
            ovls = new ArrayList<>();
            namedOverlays.put(calculatdRouteKey, ovls);
        }

        if (route == null || route.route == null || route.route.size() <= 0)
            return;
        if (route.range == null || route.range.size() <= 0)
            return;

//        Path shape = new Path();
//        shape.moveTo(0, 40);
//        shape.lineTo(20, 0);
//        shape.lineTo(40, 40);
//        PathEffect pathEffect = new PathDashPathEffect(shape, 100, 20, PathDashPathEffect.Style.MORPH);

        Paint paint = new Paint();
        paint.setAntiAlias(true);
//        if (route.start != null)
//            paint.setColor(route.start.color);
//        else if (route.end != null)
//            paint.setColor(route.end.color);
//        else if (route.way != null && route.way.length > 0)
//            paint.setColor(route.way[0].color);
//        else
        paint.setColor(route.presentationStyle.fillColor);
        paint.setAlpha(route.presentationStyle.opacity);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setPathEffect(new CornerPathEffect(20));
        paint.setStrokeWidth(route.presentationStyle.lineWidth);

        calculatedRoute = route;
        // 绘制室外路线
        int index = 0;
        for (String range : calculatedRoute.range) {
            if (range.equalsIgnoreCase("Outdoor")) {
                com.supermap.services.components.commontypes.Path[] paths = calculatedRoute.route.get(index);
                if (paths == null)
                    continue;
                for (com.supermap.services.components.commontypes.Path path : paths) {
                    if (path.route != null && path.route.points != null && path.route.points.length > 0) {
                        List<Point2D> point2DS = new ArrayList<>();
                        for (com.supermap.services.components.commontypes.Point2D point : path.route.points)
                            point2DS.add(new Point2D(point.x, point.y));
                        LineOverlay overlay = new LineOverlay(paint);
                        overlay.setLinePaint(paint);
                        overlay.setPointPaint(paint);
                        overlay.setShowPoints(false);
                        overlay.setData(point2DS);
                        ovls.add(overlay);
                    }
                }
            }
            index++;
        }
        // 绘制室内路线
        index = 0;
        for (String range : calculatedRoute.range) {
            if (range.equalsIgnoreCase(currIndoorMap)) {
                com.supermap.services.components.commontypes.Path[] paths = calculatedRoute.route.get(index);
                if (paths != null) {
                    for (com.supermap.services.components.commontypes.Path path : paths) {
                        if (path == null)
                            continue;
                        if (path.route != null && path.route.points != null && path.route.points.length > 0) {
                            List<Point2D> point2DS = new ArrayList<>();
                            for (com.supermap.services.components.commontypes.Point2D point : path.route.points)
                                point2DS.add(new Point2D(point.x, point.y));
                            LineOverlay overlay = new LineOverlay(paint);
                            overlay.setLinePaint(paint);
                            overlay.setPointPaint(paint);
                            overlay.setShowPoints(false);
                            overlay.setData(point2DS);
                            ovls.add(overlay);
                        }
                    }
                }
            }
            index++;
        }

        DefaultItemizedOverlay overlay = new DefaultItemizedOverlay(null);
        Marker marker;
        OverlayItemEx item;
        MarkerDrawable drawable;
        if (route.start != null) {
            if (route.start.marker != null) {
                marker = new Marker(route.start.coords, "起", route.start.mkWidth, route.start.mkHeight, route.start);
                item = new OverlayItemEx(
                        new Point2D(marker.position[1], marker.position[0]),
                        marker.markerId, marker.markerId, marker);
                drawable = new MarkerDrawable(route.start.marker, marker.width, marker.height);
                item.setMarker(drawable);
                overlay.addItem(item);
            }
        }
        if (route.end != null) {
            if (route.end.marker != null) {
                marker = new Marker(route.end.coords, "终", route.end.mkWidth, route.end.mkHeight, route.end);
                item = new OverlayItemEx(
                        new Point2D(marker.position[1], marker.position[0]),
                        marker.markerId, marker.markerId, marker);
                drawable = new MarkerDrawable(route.end.marker, marker.width, marker.height);
                item.setMarker(drawable);
                overlay.addItem(item);
            }
        }
        if (route.way != null) {
            for (int wi = 0; wi < route.way.length; wi++) {
                if (!TextUtils.isEmpty(route.way[wi].floorid)) {
                    String l;
                    if (TextUtils.isEmpty(route.way[wi].buildingId))
                        l = String.format("Basement:%s", route.way[wi].floorid);
                    else
                        l = String.format("%s:%s", route.way[wi].buildingId, route.way[wi].floorid);
                    if (!currIndoorMap.equalsIgnoreCase(l))
                        continue;
                }
                if (route.way[wi].marker != null) {
                    marker = new Marker(route.way[wi].coords, "终", route.way[wi].mkWidth, route.way[wi].mkHeight, route.way[wi]);
                    item = new OverlayItemEx(
                            new Point2D(marker.position[1], marker.position[0]),
                            marker.markerId, marker.markerId, marker);
                    drawable = new MarkerDrawable(route.way[wi].marker, marker.width, marker.height);
                    item.setMarker(drawable);
                    overlay.addItem(item);
                }
            }
        }
        for (List<NetWorkAnalystUtil.WayPoint> wayPoints : route.wayPoints) {
            for (NetWorkAnalystUtil.WayPoint wayPoint : wayPoints) {
                if (!TextUtils.isEmpty(wayPoint.floor)) {
                    String l;
                    if (TextUtils.isEmpty(wayPoint.building))
                        l = String.format("Basement:%s", wayPoint.floor);
                    else
                        l = String.format("%s:%s", wayPoint.building, wayPoint.floor);
                    if (!currIndoorMap.equalsIgnoreCase(l))
                        continue;

                    if (wayPoint.catalog.equalsIgnoreCase("Lift")) {
                        if (defaultFacilities.containsKey("Lift")) {
                            GeneralMarker mk = defaultFacilities.get("Lift");
                            if (mk == null)
                                continue;

                            double[] coords = new double[]{wayPoint.point.y, wayPoint.point.x};
                            String mkid = UUID.randomUUID().toString().replaceAll("-", "");
                            marker = new Marker(coords, mkid, mk.width, mk.height, null);
                            item = new OverlayItemEx(
                                    new Point2D(marker.position[1], marker.position[0]),
                                    marker.markerId, marker.markerId, marker);
                            drawable = new MarkerDrawable(mk.image, mk.width, mk.height);
                            item.setMarker(drawable);
                            overlay.addItem(item);
                        }
                    } else if (wayPoint.catalog.equalsIgnoreCase("InOut")) {
                        if (defaultFacilities.containsKey("InOut")) {
                            GeneralMarker mk = defaultFacilities.get("InOut");
                            if (mk == null)
                                continue;

                            double[] coords = new double[]{wayPoint.point.y, wayPoint.point.x};
                            String mkid = UUID.randomUUID().toString().replaceAll("-", "");
                            marker = new Marker(coords, mkid, mk.width, mk.height, null);
                            item = new OverlayItemEx(
                                    new Point2D(marker.position[1], marker.position[0]),
                                    marker.markerId, marker.markerId, marker);
                            drawable = new MarkerDrawable(mk.image, mk.width, mk.height);
                            item.setMarker(drawable);
                            overlay.addItem(item);
                        }
                    }
                }
            }
        }
        ovls.add(overlay);
        mapView.getOverlays().addAll(ovls);
        mapView.invalidate();
    }

    /**
     * 渲染边界
     * @param perimeter
     */
    private void renderPerimeter(QueryUtils.PerimeterResult perimeter) {
        List<Overlay> ovls;
        if (namedOverlays.containsKey(perimeterKey)) {
            ovls = namedOverlays.get(perimeterKey);
        } else {
            ovls = new ArrayList<>();
            namedOverlays.put(perimeterKey, ovls);
        }

        if (perimeter.normalGeometry != null) {
            Paint normalPaint = new Paint();
            normalPaint.setAntiAlias(true);
            normalPaint.setColor(Color.BLUE);
            normalPaint.setAlpha(150);
            normalPaint.setStyle(Paint.Style.STROKE);
            normalPaint.setStrokeJoin(Paint.Join.ROUND);
            normalPaint.setPathEffect(new CornerPathEffect(5));
            normalPaint.setStrokeWidth(10);

            for (List<Point2D> point2DS : perimeter.normalGeometry) {
                if (point2DS.size() > 0) {
                    LineOverlay overlay = new LineOverlay(normalPaint);
                    overlay.setLinePaint(normalPaint);
                    overlay.setShowPoints(false);
                    overlay.setData(point2DS);
                    ovls.add(overlay);
                    mapView.getOverlays().add(overlay);
                }
            }
        }

        if (perimeter.alarmGeometry != null) {
            Paint alarmPaint = new Paint();
            alarmPaint.setAntiAlias(true);
            alarmPaint.setColor(Color.RED);
            alarmPaint.setAlpha(150);
            alarmPaint.setStyle(Paint.Style.STROKE);
            alarmPaint.setStrokeJoin(Paint.Join.ROUND);
            alarmPaint.setPathEffect(new CornerPathEffect(5));
            alarmPaint.setStrokeWidth(10);

            for (List<Point2D> point2DS : perimeter.alarmGeometry) {
                if (point2DS.size() > 0) {
                    LineOverlay overlay = new LineOverlay(alarmPaint);
                    overlay.setLinePaint(alarmPaint);
                    overlay.setShowPoints(false);
                    overlay.setData(point2DS);
                    ovls.add(overlay);
                    mapView.getOverlays().add(overlay);
                }
            }
        }

        mapView.invalidate();
    }

    /**
     * 渲染模型高亮
     * @param model
     */
    private void renderModel(QueryUtils.ModelResult model) {
        List<Overlay> ovls;
        if (namedOverlays.containsKey(modelsKey)) {
            ovls = namedOverlays.get(modelsKey);
            for (Overlay ov: ovls)
                mapView.getOverlays().remove(ov);
            ovls.clear();
        } else {
            ovls = new ArrayList<>();
            namedOverlays.put(modelsKey, ovls);
        }

        if (isShowHighLight) {
            //高亮样式
            if (model.highlightGeometry != null) {
                for (int i=0; i<model.highlightGeometry.size(); i++) {
                    List<Point2D> point2DS = model.highlightGeometry.get(i);
                    if (point2DS.size() > 0) {
                        PresentationStyle ps = model.highlightStyle.get(i);
                        Paint paint = new Paint();
                        paint.setAntiAlias(true);
                        paint.setColor(ps.fillColor);
                        paint.setAlpha(ps.opacity);
                        paint.setStyle(Paint.Style.FILL_AND_STROKE);
                        paint.setStrokeJoin(Paint.Join.ROUND);
                        paint.setStrokeWidth(ps.lineWidth);

                        PolygonOverlay lot = new PolygonOverlay(paint);
                        lot.setShowPoints(false);
                        lot.setData(point2DS);
                        ovls.add(lot);
                    }
                }
            }

            //默认样式
            if (model.normalGeometry != null) {
                Paint normalPaint = new Paint();
                normalPaint.setAntiAlias(true);
                normalPaint.setColor(model.normalStyle.fillColor);
                normalPaint.setAlpha(model.normalStyle.opacity);
                normalPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                normalPaint.setStrokeJoin(Paint.Join.ROUND);
                normalPaint.setStrokeWidth(model.normalStyle.lineWidth);

                for (List<Point2D> point2DS : model.normalGeometry) {
                    if (point2DS.size() > 0) {
                        PolygonOverlay lot = new PolygonOverlay(normalPaint);
                        lot.setShowPoints(false);
                        lot.setData(point2DS);
                        ovls.add(lot);
                    }
                }
            }
        }
        if (ovls != null && ovls.size() > 0)
            mapView.getOverlays().addAll(ovls);

        mapView.invalidate();
    }

    private Paint getBuildingSelectPaint(boolean select) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.parseColor("#2262CC"));
        if (select)
            paint.setAlpha(128);
        else
            paint.setAlpha(0);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setStrokeWidth(10);

        return paint;
    }
}
