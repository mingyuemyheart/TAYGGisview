package gis.hmap;

import java.util.HashMap;

/**
 * Created by Ryan on 2019/5/15.
 */

public class ModelEvent {
    public TargetEvent eventType;
    public double[] pos;
    public String buildingId;
    public String floorId;
    public String modelId;
    private HashMap<String, String> fields;

    public ModelEvent(HashMap<String, String> fields) {
        this.fields = fields;
        buildingId = getStrParam("BUILDINGID");
        floorId = getStrParam("FLOORID");
        modelId = getStrParam("SMID");
    }

    public ModelEvent(TargetEvent eventType, double[] pos, HashMap<String, String> fields) {
        this.eventType = eventType;
        this.pos = pos;
        this.fields = fields;
        buildingId = getStrParam("BUILDINGID");
        floorId = getStrParam("FLOORID");
        modelId = getStrParam("SMID");
    }

    public double[] getPosition() {
        double[] ret = null;
        ret = new double[] {
                getNumParam("SMSDRIW"),
                getNumParam("SMSDRIN"),
                getNumParam("SMSDRIE"),
                getNumParam("SMSDRIS")
        };

        return ret;
    }

    public String getStrParam(String key) {
        String ret = "";
        if (fields.containsKey(key)) {
            ret = fields.get(key);
        }
        return ret;
    }

    public double getNumParam(String key) {
        double ret = 0;
        if (fields.containsKey(key)) {
            try {
                ret = Double.parseDouble(fields.get(key));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return ret;
    }

    public int getIntParam(String key) {
        int ret = 0;
        if (fields.containsKey(key)) {
            try {
                ret = Integer.parseInt(fields.get(key));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return ret;
    }
}
