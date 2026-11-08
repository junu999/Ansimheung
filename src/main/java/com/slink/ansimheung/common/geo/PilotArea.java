package com.slink.ansimheung.common.geo;

import com.slink.ansimheung.common.error.ApiException;
import com.slink.ansimheung.common.error.ErrorCode;
import com.slink.ansimheung.config.AnsimheungProperties;
import org.springframework.stereotype.Component;

/**
 * 시범 지역(정왕초 반경 500m, P-05) 안인지 검사.
 * 제보 등록·묶기 후보·경로 출발지에서 공통으로 사용:
 * <pre>pilotArea.requireInside(lat, lng);</pre>
 */
@Component
public class PilotArea {

    private final AnsimheungProperties.Area area;

    public PilotArea(AnsimheungProperties props) {
        this.area = props.area();
    }

    public boolean contains(double lat, double lng) {
        return GeoUtils.isValidLatLng(lat, lng)
                && GeoUtils.distanceM(area.centerLat(), area.centerLng(), lat, lng) <= area.radiusM();
    }

    /** 밖이면 OUT_OF_AREA(400) */
    public void requireInside(double lat, double lng) {
        requireInside(lat, lng, null);
    }

    /** 화면마다 다른 안내 문구가 필요할 때 (예: 경로 "시범 지역 안에서 출발지를 골라주세요") */
    public void requireInside(double lat, double lng, String message) {
        if (!contains(lat, lng)) {
            throw new ApiException(ErrorCode.OUT_OF_AREA, message);
        }
    }

    public double centerLat() {
        return area.centerLat();
    }

    public double centerLng() {
        return area.centerLng();
    }
}
