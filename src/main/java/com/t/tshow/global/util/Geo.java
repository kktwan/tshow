package com.t.tshow.global.util;

/** 좌표 계산 도우미 */
public final class Geo {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private Geo() {
    }

    /** 두 좌표 사이 거리(m). 하버사인 공식 */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(h));
    }

    /** 중심에서 반경 안의 점을 모두 담는 사각형 [최소 위도, 최대 위도, 최소 경도, 최대 경도]. DB 에서 먼저 대충 거를 때 쓴다 */
    public static double[] boundingBox(double lat, double lon, double radiusMeters) {
        double dLat = Math.toDegrees(radiusMeters / EARTH_RADIUS_METERS);
        double dLon = Math.toDegrees(radiusMeters / (EARTH_RADIUS_METERS * Math.cos(Math.toRadians(lat))));
        return new double[]{lat - dLat, lat + dLat, lon - dLon, lon + dLon};
    }
}
