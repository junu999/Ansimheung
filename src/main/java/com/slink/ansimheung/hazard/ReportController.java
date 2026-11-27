package com.slink.ansimheung.hazard;

import com.slink.ansimheung.auth.LoginUser;
import com.slink.ansimheung.hazard.dto.NearbyCandidate;
import com.slink.ansimheung.hazard.dto.NearbyQuery;
import com.slink.ansimheung.hazard.dto.ReportCreateRequest;
import com.slink.ansimheung.hazard.dto.ReportCreated;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 제보 등록·묶기 후보 조회는 회원만 (SecurityConfig). 등록 요청은 multipart: lat, lng, type, timeSlot, description?, mergeHazardId? */
@RestController
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** 제보 화면에서 위치·유형을 고르면 호출 — 20m 안 같은 유형 진행 중 지점 (#25) */
    @GetMapping("/api/hazards/nearby")
    public NearbyCandidate nearby(@Valid @ModelAttribute NearbyQuery query) {
        return reportService.nearby(query);
    }

    @PostMapping("/api/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportCreated create(@Valid @ModelAttribute ReportCreateRequest request,
                                @AuthenticationPrincipal LoginUser user) {
        return reportService.create(user.id(), request);
    }
}
