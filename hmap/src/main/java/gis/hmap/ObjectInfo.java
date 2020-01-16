package gis.hmap;


/**
 * Created by Ryan on 2018/12/7.
 */

public class ObjectInfo {
    public String address;
    public double lng;
    public double lat;
    public String[] fields;
    public String[] values;

    public String parkId = "";
    public String roomCode = "";
    public String parkX = "";
    public String parkY = "";
    public String zoneX = "";
    public String zoneY = "";
    public String cnName = "";
    public String enName = "";

    public ObjectInfo(String[] fields, String[] values) {
        this.fields = fields;
        this.values = values;
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

    public double[] getCenter() {
        double lng = (getNumParam("SMSDRIW") + getNumParam("SMSDRIE")) / 2;
        double lat = (getNumParam("SMSDRIN") + getNumParam("SMSDRIS")) / 2;

        return new double[] { lat, lng };
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
}
