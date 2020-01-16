package gis.hmap;

/**
 * Created by Ryan on 2018/9/25.
 */

public class BuildingEvent {
    public TargetEvent eventType;
    public double[] pos;
    public String buildingId;
    private String[] fields;
    private String[] values;

    public BuildingEvent(String[] fields, String[] values) {
        this.fields = fields;
        this.values = values;
        buildingId = getStrParam("BUILDINGID");
    }

    public BuildingEvent(TargetEvent eventType, double[] pos, String[] fields, String[] values) {
        this.eventType = eventType;
        this.pos = pos;
        this.fields = fields;
        this.values = values;
        buildingId = getStrParam("BUILDINGID");
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
        int index = 0;
        for (String k : fields) {
            if (key.equalsIgnoreCase(k)) {
                ret = values[index];
                break;
            }
            index++;
        }
        return ret;
    }

    public double getNumParam(String key) {
        double ret = 0;
        int index = 0;
        for (String k : fields) {
            if (key.equalsIgnoreCase(k)) {
                try {
                    ret = Double.parseDouble(values[index]);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }
            index++;
        }
        return ret;
    }

    public int getIntParam(String key) {
        int ret = 0;
        int index = 0;
        for (String k : fields) {
            if (key.equalsIgnoreCase(k)) {
                try {
                    ret = Integer.parseInt(values[index]);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }
            index++;
        }
        return ret;
    }

    public Object getTag() {
        return null;
    }
}