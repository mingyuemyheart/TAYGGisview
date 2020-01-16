package gis.hmap;

import android.content.Context;
import android.graphics.Path;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Created by Ryan on 2018/10/18.
 */

public class Common {
    private static Common _instance = null;
    private String host = "http://192.168.1.100:8090/iserver";
    private String rtlsLicenseHost = "https://10.240.155.52:18889";
    private String workSpace = "china400";
    private String parkId = "China";
    private List<String> extParam = new ArrayList<>();
    private String parkIdName = "ID";
    private String parkCenterNameX = "ParkX";
    private String parkCenterNameY = "ParkY";
    private Logger logger = null;
    private String globalWorkSpace = "HWYQ";
    private String globalParkId = "HWYQ";
    private String globalDataset = "boundary";

    private Common() { _instance = this; }

    public static void CreateInstance(String gisUrl) {
        if (_instance == null)
            _instance = new Common();

        _instance.host = gisUrl;
    }

    public static void initRtlsLicenseHost(String rtlsUrl) {
        if (_instance == null)
            _instance = new Common();

        _instance.rtlsLicenseHost = rtlsUrl;
    }

    public static String getHost() {
        if (_instance == null)
            _instance = new Common();

        return _instance.host;
    }

    public static String getRtlsLicenseSrv() {
        if (_instance == null)
            _instance = new Common();

        return _instance.rtlsLicenseHost + LicenseServer;
    }

    public static void setGlobalZone(String workspace, String parkId, String dataset) {
        if (_instance == null)
            _instance = new Common();

        _instance.globalWorkSpace = workspace;
        _instance.globalParkId = parkId;
        _instance.globalDataset = dataset;
    }

    public static void setParkKeys(String idName, String centerXName, String centerYName) {
        if (_instance == null)
            _instance = new Common();

        _instance.parkIdName = idName;
        _instance.parkCenterNameX = centerXName;
        _instance.parkCenterNameY = centerYName;
    }

    /**
     * 设置当前园区信息
     * @param workspace
     * @param parkId
     */
    public static void setCurrentZone(String workspace, String parkId) {
        if (_instance == null)
            _instance = new Common();

        _instance.workSpace = workspace;
        _instance.parkId = parkId;
    }

    public static String globalWorkSpace() {
        if (_instance == null)
            _instance = new Common();

        return _instance.globalWorkSpace;
    }

    public static String globalParkId() {
        if (_instance == null)
            _instance = new Common();

        return _instance.globalParkId;
    }

    public static String globalDataset() {
        if (_instance == null)
            _instance = new Common();

        return _instance.globalDataset;
    }

    public static String parkIdName() {
        if (_instance == null)
            _instance = new Common();

        return _instance.parkIdName;
    }

    public static String parkCenterNameX() {
        if (_instance == null)
            _instance = new Common();

        return _instance.parkCenterNameX;
    }

    public static String parkCenterNameY() {
        if (_instance == null)
            _instance = new Common();

        return _instance.parkCenterNameY;
    }

    public static String workSpace() {
        if (_instance == null)
            _instance = new Common();

        return _instance.workSpace;
    }

    public static String parkId() {
        if (_instance == null)
            _instance = new Common();

        return _instance.parkId;
    }

    public static void setExtParam(List<String> extParam) {
        if (_instance == null)
            _instance = new Common();

        _instance.extParam = extParam;
    }

    public static List<String> extParam() {
        if (_instance == null)
            _instance = new Common();

        return _instance.extParam;
    }

    public static Logger getLogger(Context context) {

        if (_instance == null)
            _instance = new Common();

        if (_instance.logger == null) {
            _instance.logger = Logger.getLogger("GisView");
            _instance.logger.setLevel(Level.ALL);
            try {
                File path = new File(context.getApplicationInfo().dataDir+ File.separator + "/gis_logs");//, Context.MODE_PRIVATE);
                if(!path.exists())
                    path.mkdir();
                File lockfile = new File(path, "common.lck");
                if (lockfile.exists())
                    lockfile.delete();
                File file = new File(path, "common.log");
                if (file.exists())
                    file.delete();
                FileHandler fileHandler = new FileHandler(file.getAbsolutePath());
                SimpleFormatter sf = new SimpleFormatter();
                fileHandler.setFormatter(sf);
                _instance.logger.addHandler(fileHandler);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        return _instance.logger;
    }

    private static final String MBTILES_ROOT = "/MBTiles/";
    private static final String MBTILES_ROOT_LOCAL = "/supermap/mbtiles";
    // SuperMap iServer提供的地图采用固定地址传递
    private static final String MAIN_MAP = "/map-{workSpace}/rest/maps/{parkId}";
    public static String MAP_URL() {
        if (_instance == null)
            return "";
        else {
            String url = MAIN_MAP.replace("{workSpace}", _instance.workSpace).replace("{parkId}", _instance.parkId);
            if (_instance.extParam.size() > 0) {
               for (int i=0; i<_instance.extParam.size(); i++) {
                   if (i == 0)
                       url += "?" + _instance.extParam.get(i);
                   else
                       url += "&" + _instance.extParam.get(i);
               }
            }
            return url;
        }
    }
    private static final String BACK_MAP = "/map-{workSpace}/rest/maps/common";
    public static String BACKMAP_URL() {
        if (_instance == null)
            return "";
        else
            return BACK_MAP.replace("{workSpace}", _instance.workSpace);
    }
    private static final String DATA = "/data-{workSpace}/rest/data";
    public static String DATA_URL() {
        if (_instance == null)
            return "";
        else
            return DATA.replace("{workSpace}", _instance.workSpace);
    }
    public static String GLOBALDATA_URL() {
        if (_instance == null)
            return "";
        else
            return DATA.replace("{workSpace}", _instance.globalWorkSpace);
    }
    private static final String TRANSPORT1 = "/transportationAnalyst-{workSpace}-{parkId}/rest/networkanalyst/{floor}_Network@{parkId}";
    private static final String TRANSPORT2 = "/transportationAnalyst-{workSpace}-{parkId}-{floor}/rest/networkanalyst/{floor}_Network@{parkId}";
    public static String TRANSPORT_URL(String floor) {
        if (_instance == null)
            return "";
        else {
            if (TextUtils.isEmpty(floor))
                return TRANSPORT1.replace("{workSpace}", _instance.workSpace).replace("{parkId}", _instance.parkId).replace("{floor}", "Park");
            else
                return TRANSPORT2.replace("{workSpace}", _instance.workSpace).replace("{parkId}", _instance.parkId).replace("{floor}", floor);
        }
    }
    private static final String GEO_CODE = "/addressmatch-{workSpace}/restjsr/v1/address/geocoding.json";
    public static String GEO_CODE_URL() {
        if (_instance == null)
            return "";
        else
            return GEO_CODE.replace("{workSpace}", _instance.workSpace);
    }
    private static final String GEO_DECODE = "/addressmatch-{workSpace}/restjsr/v1/address/geodecoding.json";
    public static String GEO_DECODE_URL() {
        if (_instance == null)
            return "";
        else
            return GEO_DECODE.replace("{workSpace}", _instance.workSpace);
    }

    //HWYQ是虚拟园区，为获取当前园区信息所用
    public static String GEO_DECODE_HWYQURL() {
        if (_instance == null)
            return "";
        else
            return GEO_DECODE.replace("{workSpace}", _instance.globalWorkSpace);
    }

    //Rtls
    private static String LicenseServer  = "/garden.guide/guide/requestguide";
    //Roma
    public static String ROMA_KEY = "";

    public static final int ADD_GENERAL_MARKER = 1;
    public static final int ADD_FLASH_MARKER = 2;
    public static final int QUERY_BUILDINGS = 10;
    public static final int QUERY_INDOOR_MAP = 11;
    public static final int QUERY_PERIMETER = 12;
    public static final int QUERY_MODEL = 13;
    public static final int QUERY_BASEMENT_MAP = 14;
    public static final int ANALYST_ROUTE = 101;//路径规划绘制
    public static final int PATH_PLAN_DATA = 102;//获取路径规划数据
    public static final int HEAT_MAP_CALC_END = 201;
    public static final int EVENT_MAP_TAP = 301;
    public static final int START_TIMER = 0;
    public static final int STOP_TIMER = -1;

    public static ExecutorService fixedThreadPool = Executors.newFixedThreadPool(10);

    public static String tileFileExists(String tilename)
    {
        String ret = null;

        try {
            String path = Environment.getExternalStorageDirectory().getAbsolutePath() + MBTILES_ROOT_LOCAL + "/" + tilename;
            File file = new File(path);
            if (file.exists())
                ret = path;
        } catch (Exception e) {
            Log.e("DOWNLOAD", "check MBTiles error: " + e.getMessage(), e);
        }

        return ret;
    }

    public static void downloadMBTiles(final String tilename)
    {
        final String url = getHost() + MBTILES_ROOT + tilename;
        final String path = Environment.getExternalStorageDirectory().getAbsolutePath() + MBTILES_ROOT_LOCAL;

        try {
            File file = new File(path);
            if(!file.exists()){
                file.mkdirs();
            }
        } catch (Exception e) {
            Log.e("DOWNLOAD", "error: " + e.getMessage(), e);
        }

        fixedThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try{
                    final long startTime = System.currentTimeMillis();
                    Log.i("DOWNLOAD","startTime="+startTime);
                    URL myURL = new URL(url);
                    URLConnection conn = myURL.openConnection();
                    conn.connect();
                    InputStream is = conn.getInputStream();
                    int fileSize = conn.getContentLength();//根据响应获取文件大小
                    if (fileSize <= 0) throw new RuntimeException("无法获知文件大小 ");
                    if (is == null) throw new RuntimeException("stream is null");
                    //把数据存入路径+文件名
                    FileOutputStream fos = new FileOutputStream(path+"/"+tilename);
                    byte buf[] = new byte[1024];
                    int downLoadFileSize = 0;
                    do{
                        //循环读取
                        int numread = is.read(buf);
                        if (numread == -1)
                        {
                            break;
                        }
                        fos.write(buf, 0, numread);
                        downLoadFileSize += numread;
                        //更新进度条
                    } while (true);

                    Log.i("DOWNLOAD","download success:" + downLoadFileSize);
                    Log.i("DOWNLOAD","totalTime="+ (System.currentTimeMillis() - startTime));

                    is.close();
                } catch (Exception ex) {
                    Log.e("DOWNLOAD", "error: " + ex.getMessage(), ex);
                }
            }
        });
    }
}
