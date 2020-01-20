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
import com.supermap.services.components.commontypes.Feature;
import com.unity3d.player.UnityPlayer;

import java.util.List;
import java.util.Map;

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
                gisView.loadMap(5, new double[]{22.66183778643608, 114.06381502747536}, "TA");
                gisView.addMapLoadedListener(new MapLoadedListener() {
                    @Override
                    public void OnMapLoaded(MapLoadedEvent me) {
                        gisView.showIndoorMap(GisView.TYPE_PARKING, "A1", "B02", new IndoorCallback() {
                            @Override
                            public void showIndoorSuccess(List<Map<String, String>> dataList) {
                                for (int i = 0; i < dataList.size(); i++) {
                                    Map<String, String> dataMap = dataList.get(i);
                                    for (Map.Entry<String, String> entry : dataMap.entrySet()) {
                                        Log.e("dataMap", entry.getKey()+","+entry.getValue());
                                    }
                                }
                            }
                        });
                        gisView.showModelHighlight("A1", "B02", new String[] {"008","009","010","011","012"});
                    }
                });

                gisView.getPathPlanData(new RoutePoint(new double[]{22.662119, 114.06337}, "A1", "B02"),
                        new RoutePoint(new double[]{22.661556, 114.06322}, "A1", "B02"), new PathPlanDataListener() {
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
