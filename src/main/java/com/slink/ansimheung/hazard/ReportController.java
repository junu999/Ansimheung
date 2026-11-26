package com.slink.ansimheung.hazard;

import com.slink.ansimheung.auth.LoginUser;
import com.slink.ansimheung.hazard.dto.ReportCreateRequest;
import com.slink.ansimheung.hazard.dto.ReportCreated;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 제보 등록은 회원만 (SecurityConfig). 요청은 multipart: lat, lng, type, timeSlot, description? */
@RestController
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/api/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportCreated create(@Valid @ModelAttribute ReportCreateRequest request,
                                @AuthenticationPrincipal LoginUser user) {
        return reportService.create(user.id(), request);
    }
}
