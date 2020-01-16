package gis.hmap;

import android.text.TextUtils;

import java.util.Map;

/**
 * Created by Ryan on 2018/11/12.
 */

public class LocationEvent {
    public boolean indoor;
    public String address;
    public double lng;
    public double lat;
    public double direction;
    public String buildingId;
    public String floorId;

    public LocationEvent(String address, double lng, double lat, double dir, String buildingId, String floorId) {
        this.address = address;
        this.lng = lng;
        this.lat = lat;
        this.direction = dir;
        this.buildingId = buildingId;
        this.floorId = floorId;
        this.indoor = !TextUtils.isEmpty(buildingId);
    }
}
