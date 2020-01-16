package gis.gisdemo

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Toast
import gis.hmap.GisView
import gis.hmap.IVASMappingData
import gis.hmap.IVASMappingListener
import gis.hmap.MyUnityActivity
import kotlinx.android.synthetic.main.activity_blank.*

class BlankActivity : Activity() {

    private val mUIHandler = Handler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blank)
        initMap()
//        startActivity(Intent(this, MyUnityActivity::class.java))
    }

    private fun initMap() {
        btnIntent.visibility = View.GONE
        btnIntent.setOnClickListener {
            startActivity(Intent(this@BlankActivity, MainActivity2::class.java))
        }

//        GisView.setGisServer("http://mcloud-uat.huawei.com/mcloud/mag/FreeProxyForText/BTYQ_json")//华为平安园区
        GisView.setGisServer("http://w3m.huawei.com/mcloud/mag/FreeProxyForText/BTYQ_json")//生产环境
        GisView.setLocDecoder(true, object : IVASMappingListener {//获取IVAS数据
            override fun onIVASMappingSuccess(iVasMapping: MutableList<IVASMappingData>?) {
                mUIHandler.post {
                    for (i in 0 until iVasMapping!!.size) {
                        Log.e("onIVASMappingSuccess$i", iVasMapping[i].ivasBuildingId)
                    }
                    Toast.makeText(this@BlankActivity, "获取IVAS数据成功", Toast.LENGTH_SHORT).show()
                    btnIntent.visibility = View.VISIBLE
                }
            }

            override fun onIVASMappingFailed(msg: String?) {
                mUIHandler.post {
                    Log.e("onIVASMappingFailed", msg)
                    Toast.makeText(this@BlankActivity, msg, Toast.LENGTH_SHORT).show()
                }
            }
        })

        //获取对应经纬度的园区信息
        GisView.queryWorkspace(114.0576151013374200, 22.6573017046106460) { loc ->
            mUIHandler.post {
                if (loc != null && loc.isNotEmpty()) {
                    for (i in 0 until loc.size) {
                        val geoLocation = loc[i]
                        if (!TextUtils.isEmpty(geoLocation.address)) {
                            Log.e("switchWorkspace", geoLocation.address)
                        }
                    }
                }
            }
        }

        //该方法由于超图没有适配android24及以上
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            GisView.enableAutoClearCache(true)
        }

        gisView.loadMap(5, doubleArrayOf(22.6573017046106460, 114.0576151013374200))
//        gisView.addMapLoadedListener { Toast.makeText(this@BlankActivity, "地图加载完成", Toast.LENGTH_SHORT).show() }

        //poi查询，获取对应经纬度下园区信息、地理位置信息等，需要实例化gisview以后使用
        gisView.getAddressOfLocation(114.0576151013374200, 22.6573017046106460) { loc ->
            mUIHandler.post {
                if (loc != null && loc.isNotEmpty()) {
                    for (i in 0 until loc.size) {
                        val geoLocation = loc[i]
                        if (!TextUtils.isEmpty(geoLocation.address)) {
                            Log.e("getAddressOfLocation", geoLocation.address)
                        }
                    }
                }
            }
        }

    }

}
