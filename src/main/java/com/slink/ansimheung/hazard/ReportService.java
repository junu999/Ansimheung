package com.slink.ansimheung.hazard;

import com.slink.ansimheung.common.error.ApiException;
import com.slink.ansimheung.common.error.ErrorCode;
import com.slink.ansimheung.common.geo.PilotArea;
import com.slink.ansimheung.common.type.HazardStatus;
import com.slink.ansimheung.config.AnsimheungProperties;
import com.slink.ansimheung.hazard.dto.ReportCreateRequest;
import com.slink.ansimheung.hazard.dto.ReportCreated;
import com.slink.ansimheung.risk.RiskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** 제보 등록 (#11). 지금은 항상 새 지점을 만든다 — 기존 지점에 묶기는 #25, 사진은 #24 */
@Service
public class ReportService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final HazardRepository hazards;
    private final ReportRepository reports;
    private final StatusHistoryRepository histories;
    private final RiskService riskService;
    private final PilotArea pilotArea;
    private final AnsimheungProperties props;

    public ReportService(HazardRepository hazards, ReportRepository reports, StatusHistoryRepository histories,
                         RiskService riskService, PilotArea pilotArea, AnsimheungProperties props) {
        this.hazards = hazards;
        this.reports = reports;
        this.histories = histories;
        this.riskService = riskService;
        this.pilotArea = pilotArea;
        this.props = props;
    }

    /** 지점 생성(접수) → 제보 → 접수 이력 → 구간 배정·점수 계산. 한 트랜잭션 */
    @Transactional
    public ReportCreated create(Long userId, ReportCreateRequest req) {
        pilotArea.requireInside(req.lat(), req.lng());
        requireDailyLimit(userId);

        Hazard hazard = Hazard.create(req.lat(), req.lng(), req.type(), req.timeSlot());
        hazard.addReport();
        hazards.save(hazard);
        Report report = reports.save(Report.create(hazard.getId(), userId, req.lat(), req.lng(),
                req.timeSlot(), req.description()));
        histories.save(StatusHistory.of(hazard.getId(), null, HazardStatus.RECEIVED, null, null));
        riskService.refresh(hazard);
        return new ReportCreated(report.getId(), hazard.getId(), false);
    }

    /** C-05: 오늘 00시(Asia/Seoul)부터 쓴 제보 수 (삭제한 제보도 셈) */
    private void requireDailyLimit(Long userId) {
        OffsetDateTime todayStart = LocalDate.now(SEOUL).atStartOfDay(SEOUL).toOffsetDateTime();
        if (reports.countByUserIdAndCreatedAtGreaterThanEqual(userId, todayStart) >= props.limits().reportsPerDay()) {
            throw new ApiException(ErrorCode.LIMIT);
        }
    }
}
