package com.slink.ansimheung.common.geo;

/** 좌표 계산 도우미 (WGS84). DB 공간 검색은 PostGIS 를 쓰고, 이건 요청 검증 같은 간단한 계산용 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_M = 6_371_008.8;

    private GeoUtils() {
    }

    /** 두 점 사이 거리(m), 하버사인 공식 */
    public static double distanceM(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }

    public static boolean isValidLatLng(double lat, double lng) {
        return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }
}
