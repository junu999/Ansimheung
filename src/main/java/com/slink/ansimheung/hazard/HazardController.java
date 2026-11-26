package com.slink.ansimheung.hazard;

import com.slink.ansimheung.auth.LoginUser;
import com.slink.ansimheung.common.error.ApiException;
import com.slink.ansimheung.common.geo.GeoUtils;
import com.slink.ansimheung.common.type.HazardType;
import com.slink.ansimheung.common.type.TimeSlot;
import com.slink.ansimheung.hazard.dto.HazardDetail;
import com.slink.ansimheung.hazard.dto.HazardFeatureCollection;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/** 지도·상세는 누구나 (SecurityConfig). timeSlot 생략 = 요청 시각(Asia/Seoul)의 시간대, ALWAYS 는 불가(V-08) */
@RestController
public class HazardController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String BBOX_MESSAGE = "지도 범위가 올바르지 않아요";

    private final HazardService hazardService;

    public HazardController(HazardService hazardService) {
        this.hazardService = hazardService;
    }

    /** bbox = 서,남,동,북 (경도,위도,경도,위도). types = 쉼표로 구분, 생략하면 전체 */
    @GetMapping("/api/hazards")
    public HazardFeatureCollection list(@RequestParam String bbox,
                                        @RequestParam(required = false) List<HazardType> types,
                                        @RequestParam(required = false) TimeSlot timeSlot) {
        EnumSet<HazardType> typeSet = types == null || types.isEmpty()
                ? EnumSet.noneOf(HazardType.class) : EnumSet.copyOf(types);
        return hazardService.list(parseBbox(bbox), typeSet, querySlot(timeSlot));
    }

    @GetMapping("/api/hazards/{id}")
    public HazardDetail detail(@PathVariable Long id, @RequestParam(required = false) TimeSlot timeSlot,
                               @AuthenticationPrincipal LoginUser user) {
        return hazardService.detail(id, user == null ? null : user.id(), querySlot(timeSlot));
    }

    private static Optional<TimeSlot> querySlot(TimeSlot timeSlot) {
        if (timeSlot == null) {
            return TimeSlot.of(LocalTime.now(SEOUL));
        }
        if (!timeSlot.isQueryable()) {
            throw ApiException.field("timeSlot", "시간대를 선택해 주세요");   // V-08
        }
        return Optional.of(timeSlot);
    }

    /** "서,남,동,북" → [west, south, east, north]. 숫자 4개 · 서<동 · 남<북 · 경위도 범위 */
    static double[] parseBbox(String bbox) {
        String[] parts = bbox.split(",");
        if (parts.length != 4) {
            throw ApiException.field("bbox", BBOX_MESSAGE);
        }
        double[] v = new double[4];
        try {
            for (int i = 0; i < 4; i++) {
                v[i] = Double.parseDouble(parts[i].strip());
            }
        } catch (NumberFormatException e) {
            throw ApiException.field("bbox", BBOX_MESSAGE);
        }
        boolean valid = v[0] < v[2] && v[1] < v[3]
                && GeoUtils.isValidLatLng(v[1], v[0]) && GeoUtils.isValidLatLng(v[3], v[2]);
        if (!valid) {
            throw ApiException.field("bbox", BBOX_MESSAGE);
        }
        return v;
    }
}
