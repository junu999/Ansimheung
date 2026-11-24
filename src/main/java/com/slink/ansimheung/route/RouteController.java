package com.slink.ansimheung.route;

import com.slink.ansimheung.common.error.ApiException;
import com.slink.ansimheung.common.type.TimeSlot;
import com.slink.ansimheung.route.dto.RouteRequest;
import com.slink.ansimheung.route.dto.RouteResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

/** POST /api/routes (공개). GET 이 아닌 이유: 출발지(사실상 집 위치)가 URL·접속 로그에 남지 않게 (P-12) */
@RestController
public class RouteController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping("/api/routes")
    public RouteResponse routes(@Valid @RequestBody RouteRequest req) {
        if (req.timeSlot() != null && !req.timeSlot().isQueryable()) {
            throw ApiException.field("timeSlot", "시간대를 선택해 주세요");   // V-08 조회에는 ALWAYS 불가
        }
        Optional<TimeSlot> slot = req.timeSlot() != null
                ? Optional.of(req.timeSlot())
                : TimeSlot.of(LocalTime.now(SEOUL));
        return routeService.routes(req.fromLat(), req.fromLng(), slot);
    }
}
