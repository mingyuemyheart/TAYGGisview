package gis.hmap;

import android.location.Location;

/**
 * Created by Ryan on 2018/11/12.
 */

class BestLocation {
    private static BestLocation _instance = null;
    private boolean isIndoor;
    private long updateTime = 0L;
    private double lastLat;
    private double lastLng;
    public double lat;
    public double lng;
    public double direction;
    public String location;
    public String buildingId;
    public String floorId;

    private BestLocation() {
        lastLat = lastLng = 0.0;
        lat = lng = 0.0;
        direction = 0.0;
        buildingId = floorId = "";
        isIndoor = false;
    }

    public static BestLocation getInstance() {
        if (_instance == null)
            _instance = new BestLocation();

        return _instance;
    }

    public static void updateIndoor(double lat, double lng, String buildingId, String floorId, long timestamp) {
        if (_instance == null)
            _instance = new BestLocation();

        _instance.lat = lat;
        _instance.lng = lng;
        _instance.buildingId = buildingId;
        _instance.floorId = floorId;
        _instance.isIndoor = true;
        _instance.updateTime = timestamp;
        _instance.calcDirection();
    }

    public static void goOutdoor() {
        if (_instance == null)
            _instance = new BestLocation();

        _instance.isIndoor = false;
    }

    public static void updateGlobal(double lat, double lng, String loc, long timestamp) {
        if (_instance == null)
            _instance = new BestLocation();

        _instance.lat = lat;
        _instance.lng = lng;
        _instance.location = loc;
        _instance.isIndoor = false;
        _instance.updateTime = timestamp;
        _instance.calcDirection();
    }

    public static boolean isIndoorValidate() {
        if (_instance == null)
            _instance = new BestLocation();

        return _instance.isIndoor;
    }

    public static boolean isGlobalValidate() {
        if (_instance == null)
            _instance = new BestLocation();

        return !_instance.isIndoor;
    }

    long getUpdateTime() {
        return updateTime;
    }

    private void calcDirection() {
        if (lastLat != 0.0 && lastLng != 0.0) {
            int MAXITERS = 20;
            double lat1 = lastLat;
            double lng1 = lastLng;
            double lat2 = lat;
            double lng2 = lng;

            // Convert lat/long to radians
            lat1 *= Math.PI / 180.0;
            lat2 *= Math.PI / 180.0;
            lng1 *= Math.PI / 180.0;
            lng2 *= Math.PI / 180.0;

            double a = 6378137.0; // WGS84 major axis
            double b = 6356752.3142; // WGS84 semi-major axis
            double f = (a - b) / a;
            double aSqMinusBSqOverBSq = (a * a - b * b) / (b * b);

            double L = lng2 - lng1;
            double A = 0.0;
            double U1 = Math.atan((1.0 - f) * Math.tan(lat1));
            double U2 = Math.atan((1.0 - f) * Math.tan(lat2));

            double cosU1 = Math.cos(U1);
            double cosU2 = Math.cos(U2);
            double sinU1 = Math.sin(U1);
            double sinU2 = Math.sin(U2);
            double cosU1cosU2 = cosU1 * cosU2;
            double sinU1sinU2 = sinU1 * sinU2;

            double sigma = 0.0;
//            double deltaSigma = 0.0;
            double cosSqAlpha = 0.0;
            double cos2SM = 0.0;
            double cosSigma = 0.0;
            double sinSigma = 0.0;
            double cosLambda = 0.0;
            double sinLambda = 0.0;

            double lambda = L; // initial guess
            for (int iter = 0; iter < MAXITERS; iter++) {
                double lambdaOrig = lambda;
                cosLambda = Math.cos(lambda);
                sinLambda = Math.sin(lambda);
                double t1 = cosU2 * sinLambda;
                double t2 = cosU1 * sinU2 - sinU1 * cosU2 * cosLambda;
                double sinSqSigma = t1 * t1 + t2 * t2; // (14)
                sinSigma = Math.sqrt(sinSqSigma);
                cosSigma = sinU1sinU2 + cosU1cosU2 * cosLambda; // (15)
                sigma = Math.atan2(sinSigma, cosSigma); // (16)
                double sinAlpha = (sinSigma == 0) ? 0.0 :
                        cosU1cosU2 * sinLambda / sinSigma; // (17)
                cosSqAlpha = 1.0 - sinAlpha * sinAlpha;
                cos2SM = (cosSqAlpha == 0) ? 0.0 :
                        cosSigma - 2.0 * sinU1sinU2 / cosSqAlpha; // (18)

                double uSquared = cosSqAlpha * aSqMinusBSqOverBSq; // defn
                A = 1 + (uSquared / 16384.0) * // (3)
                        (4096.0 + uSquared *
                                (-768 + uSquared * (320.0 - 175.0 * uSquared)));
                double B = (uSquared / 1024.0) * // (4)
                        (256.0 + uSquared *
                                (-128.0 + uSquared * (74.0 - 47.0 * uSquared)));
                double C = (f / 16.0) *
                        cosSqAlpha *
                        (4.0 + f * (4.0 - 3.0 * cosSqAlpha)); // (10)
//                double cos2SMSq = cos2SM * cos2SM;
//                deltaSigma = B * sinSigma * // (6)
//                        (cos2SM + (B / 4.0) *
//                                (cosSigma * (-1.0 + 2.0 * cos2SMSq) -
//                                        (B / 6.0) * cos2SM *
//                                                (-3.0 + 4.0 * sinSigma * sinSigma) *
//                                                (-3.0 + 4.0 * cos2SMSq)));

                lambda = L +
                        (1.0 - C) * f * sinAlpha *
                                (sigma + C * sinSigma *
                                        (cos2SM + C * cosSigma *
                                                (-1.0 + 2.0 * cos2SM * cos2SM))); // (11)

                double delta = (lambda - lambdaOrig) / lambda;
                if (Math.abs(delta) < 1.0e-12) {
                    break;
                }
            }

//            float distance = (float) (b * A * (sigma - deltaSigma));
            float initialBearing = (float) Math.atan2(cosU2 * sinLambda,
                    cosU1 * sinU2 - sinU1 * cosU2 * cosLambda);
            initialBearing *= 180.0 / Math.PI;
            while (initialBearing >= 360.0)
                initialBearing -= 360.0;
            while (initialBearing < 0)
                initialBearing += 360.0;
            direction = initialBearing;
//            float finalBearing = (float) Math.atan2(cosU1 * sinLambda,
//                    -sinU1 * cosU2 + cosU1 * sinU2 * cosLambda);
//            finalBearing *= 180.0 / Math.PI;
//            results[2] = finalBearing;
        }

        lastLat = lat;
        lastLng = lng;
    }
}
