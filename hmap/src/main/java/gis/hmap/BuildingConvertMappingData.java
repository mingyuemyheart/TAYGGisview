package gis.hmap;

import com.supermap.services.components.commontypes.Feature;

public class BuildingConvertMappingData {
    public double lat;
    public double lng;
    public String buildingId;
    public String[] floorList;
    public String targetId;

    public BuildingConvertMappingData(Feature feature) {
        for (int i=0; i<feature.fieldNames.length; i++) {
            if (feature.fieldNames[i].equalsIgnoreCase("SMX"))
                lng = Double.parseDouble(feature.fieldValues[i]);
            else if (feature.fieldNames[i].equalsIgnoreCase("SMY"))
                lat = Double.parseDouble(feature.fieldValues[i]);
            else if (feature.fieldNames[i].equalsIgnoreCase("BUILDINGID"))
                buildingId = feature.fieldValues[i];
            else if (feature.fieldNames[i].equalsIgnoreCase("FLOORLIST"))
                floorList = feature.fieldValues[i].split(",");
            else if (feature.fieldNames[i].equalsIgnoreCase("BASEMENTBUILDINGID"))
                targetId = feature.fieldValues[i];
        }
    }
}
