package gis.hmap;

public class MapCacheEvent {
    public boolean success;
    public String cacheType;

    public MapCacheEvent(boolean success, String type) {
        this.success = success;
        this.cacheType = type;
    }
}
