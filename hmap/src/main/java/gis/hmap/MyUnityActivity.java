package gis.hmap;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.supermap.android.maps.Point2D;
import com.unity3d.player.UnityPlayer;

import java.util.List;

public class MyUnityActivity extends Activity {

    private Handler mUIHandler = new Handler();
    protected UnityPlayer unityPlayer;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        unityPlayer = new UnityPlayer(this);
        if (unityPlayer.getSettings ().getBoolean ("hide_status_bar", true)) {
            getWindow ().setFlags (WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        int glesMode = unityPlayer.getSettings().getInt("gles_mode", 1);
        boolean trueColor8888 = false;
        unityPlayer.init(glesMode, trueColor8888);

        View playerView = unityPlayer.getView();
        setContentView(R.layout.activity_myunity);

        RelativeLayout unityContainer = findViewById(R.id.unityContainer);
        unityContainer.addView(playerView, 0);
    }

    /**
     * 供unity端调用
     */
    public void showView() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                final GisView gisView = findViewById(R.id.gisView);
                final Button button = findViewById(R.id.button);
                button.setVisibility(View.VISIBLE);

                GisView.setGisServer("http://w3m.huawei.com/mcloud/mag/FreeProxyForText/BTYQ_json");//生产环境
                gisView.setLogEnable(true);
                gisView.setMaxZoomLevel(18);
                gisView.loadMap(5, new double[]{22.6573017046106460, 114.0576151013374200});

                //获取对应经纬度的园区信息
                GisView.queryWorkspace(114.0576151013374200, 22.6573017046106460, new QueryWorkspaceListener() {
                    @Override
                    public void onQueryWorkspace(GeoLocation[] loc) {
                        if (loc != null && loc.length > 0) {
                            for (int i = 0; i < loc.length; i++) {
                                GeoLocation geoLocation = loc[i];
                                if (!TextUtils.isEmpty(geoLocation.address)) {
                                    Log.e("switchWorkspace", geoLocation.address);
                                }
                            }
                        }
                    }
                });

                gisView.getPathPlanData(new RoutePoint(new double[]{22.655674, 114.05721}, "J01", "F01"),
                        new RoutePoint(new double[]{22.65592, 114.05719}, "J01", "F01"), new PathPlanDataListener() {
                            @Override
                            public void pathPlanDataSuccess(final List<Point2D> point2DS) {
                                mUIHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        String result = "";
                                        for (int i = 0; i < point2DS.size(); i++) {
                                            Point2D point = point2DS.get(i);
                                            result += String.format("%s---%s", point.x, point.y)+"\n";
                                            Log.e("pathPlanDataSuccess", String.format("%s---%s", point.x, point.y));
                                        }
                                        Toast.makeText(MyUnityActivity.this, result, Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                            @Override
                            public void pathPlanDataFailed(final String msg) {
                                mUIHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(MyUnityActivity.this, msg, Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        });


                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (gisView.getVisibility() == View.VISIBLE) {
                            gisView.setVisibility(View.GONE);
                            button.setText("显示");
                        } else {
                            gisView.setVisibility(View.VISIBLE);
                            button.setText("隐藏");
                        }
                    }
                });
            }
        });

    }

    public static void test1() {
        Log.e("test1", "测试toast弹出");
    }

    public static void test2() {
        GisView.setGisServer("http://w3m.huawei.com/mcloud/mag/FreeProxyForText/BTYQ_json");
        GisView.queryWorkspace(114.0576151013374200, 22.6573017046106460, new QueryWorkspaceListener() {
            @Override
            public void onQueryWorkspace(GeoLocation[] loc) {
                if (loc != null && loc.length > 0) {
                    for (int i = 0; i < loc.length; i++) {
                        GeoLocation geoLocation = loc[i];
                        if (!TextUtils.isEmpty(geoLocation.address)) {
                            Log.e("queryWorkspace", geoLocation.address);
                        }
                    }
                }
            }
        });
    }

    public void test3() {
        GisView.setGisServer("http://w3m.huawei.com/mcloud/mag/FreeProxyForText/BTYQ_json");
        GisView gisView = (GisView) getLayoutInflater().inflate(R.layout.gisview, null);
        gisView.getPathPlanData(new RoutePoint(new double[]{22.655674, 114.05721}, "J01", "F01"),
                new RoutePoint(new double[]{22.65592, 114.05719}, "J01", "F01"), new PathPlanDataListener() {
                    @Override
                    public void pathPlanDataSuccess(List<Point2D> point2DS) {
                        for (int i = 0; i < point2DS.size(); i++) {
                            Point2D point = point2DS.get(i);
                            Log.e("pathPlanDataSuccess", String.format("%s---%s", point.x, point.y));
                        }
                    }

                    @Override
                    public void pathPlanDataFailed(String msg) {
                        Log.e("pathPlanDataFailed", msg);
                    }
                });
    }

    public void test4() {
        GisView.setGisServer("http://w3m.huawei.com/mcloud/mag/FreeProxyForText/BTYQ_json");
        GisView.queryWorkspace(114.0576151013374200, 22.6573017046106460, new QueryWorkspaceListener() {
            @Override
            public void onQueryWorkspace(final GeoLocation[] loc) {
                mUIHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        String result = "";
                        if (loc != null && loc.length > 0) {
                            for (int i = 0; i < loc.length; i++) {
                                GeoLocation geoLocation = loc[i];
                                if (!TextUtils.isEmpty(geoLocation.address)) {
                                    result += geoLocation.address+"\n";
                                    Log.e("queryWorkspace", geoLocation.address);
                                }
                            }
                        }
                        Toast.makeText(MyUnityActivity.this, result, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    protected void onDestroy() {
        unityPlayer.quit();
        super.onDestroy();
    }

    // onPause()/onResume() must be sent to UnityPlayer to enable pause and resource recreation on resume.
    protected void onPause() {
        super.onPause();
        unityPlayer.pause();
    }

    protected void onResume() {
        super.onResume();
        unityPlayer.resume();
    }

    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        unityPlayer.configurationChanged(newConfig);
    }

    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        unityPlayer.windowFocusChanged(hasFocus);
    }

}
