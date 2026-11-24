package com.slink.ansimheung.route.dto;

import com.slink.ansimheung.common.type.RouteMode;

import java.util.List;

/** POST /api/routes 응답 = static/mock/routes.json 모양 */
public record RouteResponse(List<Route> routes) {

    /**
     * @param distanceM    m, 정수 반올림
     * @param minutes      distanceM ÷ 분속(C-11), 반올림
     * @param riskExposure 지나는 구간 edgeRisk 합 (조회 시간대 반영), 소수 첫째 자리 반올림
     * @param hazardIds    지나는 구간에 배정된 위험 지점 id (지나는 순서, 중복 없음)
     */
    public record Route(RouteMode mode, double lambda, long distanceM, long minutes, double riskExposure,
                        List<Long> hazardIds, LineString path) {
    }

    /** GeoJSON LineString — 좌표는 [경도, 위도] */
    public record LineString(String type, List<double[]> coordinates) {

        public static LineString of(List<double[]> lngLats) {
            return new LineString("LineString", lngLats);
        }
    }
}
