package gis.hmap;

public class ExternLocData {
    public double lng;
    public double lat;
    public String buildingId;
    public String floorId;
    public String roomCode;
    public String[] fields;
    public String[] values;

    public ExternLocData(double lat, double lng, String buildingId, String floorId, String roomCode, String[] fields, String[] values) {
        this.lng = lng;
        this.lat = lat;
        this.buildingId = buildingId;
        this.floorId = floorId;
        this.roomCode = roomCode;
        this.fields = fields;
        this.values = values;

    }
}
