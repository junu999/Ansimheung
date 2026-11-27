package com.slink.ansimheung.hazard;

import com.slink.ansimheung.common.error.ApiException;
import com.slink.ansimheung.common.error.ErrorCode;
import com.slink.ansimheung.common.geo.GeoUtils;
import com.slink.ansimheung.common.geo.PilotArea;
import com.slink.ansimheung.common.type.HazardStatus;
import com.slink.ansimheung.config.AnsimheungProperties;
import com.slink.ansimheung.hazard.dto.NearbyCandidate;
import com.slink.ansimheung.hazard.dto.NearbyQuery;
import com.slink.ansimheung.hazard.dto.ReportCreateRequest;
import com.slink.ansimheung.hazard.dto.ReportCreated;
import com.slink.ansimheung.risk.RiskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

/**
 * 제보 등록 (#11) · 20m 중복 묶기 (#25). 서버가 조용히 합치지 않는다 (P-02):
 * 화면이 nearby 로 후보를 보여 주고, 사용자가 고르면 mergeHazardId 를 보낸다. 사진은 #24
 */
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

    /** 묶기 후보 (P-01): 반경 20m · 같은 유형 · 접수/확인중 · 숨김 아님 중 가장 가까운 1곳 */
    @Transactional(readOnly = true)
    public NearbyCandidate nearby(NearbyQuery q) {
        pilotArea.requireInside(q.lat(), q.lng());
        List<String> open = Arrays.stream(HazardStatus.values()).filter(HazardStatus::isOpen).map(Enum::name).toList();
        return new NearbyCandidate(hazards.findMergeCandidate(q.lat(), q.lng(), q.type().name(), open, props.merge().radiusM())
                .map(h -> new NearbyCandidate.Candidate(h.getId(),
                        Math.round(GeoUtils.distanceM(q.lat(), q.lng(), h.getLat(), h.getLng())),
                        h.getEmpathyCount(), h.getReportCount()))
                .orElse(null));
    }

    /**
     * mergeHazardId 없음: 지점 생성(접수) → 제보 → 접수 이력 → 구간 배정·점수 계산.
     * mergeHazardId 있음: 그 지점에 제보 추가 → 제보 수 +1 → 재계산. 한 트랜잭션
     */
    @Transactional
    public ReportCreated create(Long userId, ReportCreateRequest req) {
        pilotArea.requireInside(req.lat(), req.lng());
        requireDailyLimit(userId);
        if (req.mergeHazardId() != null) {
            return merge(userId, req);
        }

        Hazard hazard = Hazard.create(req.lat(), req.lng(), req.type(), req.timeSlot());
        hazard.addReport();
        hazards.save(hazard);
        Report report = reports.save(Report.create(hazard.getId(), userId, req.lat(), req.lng(),
                req.timeSlot(), req.description()));
        histories.save(StatusHistory.of(hazard.getId(), null, HazardStatus.RECEIVED, null, null));
        riskService.refresh(hazard);
        return new ReportCreated(report.getId(), hazard.getId(), false);
    }

    /**
     * 없음·숨김·반려 → NOT_FOUND. 유형이 다름·개선완료·반경 밖 → STATE_CONFLICT (그사이 상태가 바뀐 경우 등).
     * 이미 내가 제보한 지점 → DUP_REPORT. 지점의 시간대는 첫 제보 그대로 둔다
     */
    private ReportCreated merge(Long userId, ReportCreateRequest req) {
        Hazard hazard = hazards.findById(req.mergeHazardId())
                .filter(h -> !h.isHidden() && h.getStatus() != HazardStatus.REJECTED)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (hazard.getType() != req.type() || !hazard.getStatus().isOpen()
                || GeoUtils.distanceM(req.lat(), req.lng(), hazard.getLat(), hazard.getLng()) > props.merge().radiusM()) {
            throw new ApiException(ErrorCode.STATE_CONFLICT);
        }
        if (reports.existsByHazardIdAndUserIdAndDeletedFalse(hazard.getId(), userId)) {
            throw new ApiException(ErrorCode.DUP_REPORT);
        }
        Report report = reports.save(Report.create(hazard.getId(), userId, req.lat(), req.lng(),
                req.timeSlot(), req.description()));
        hazard.addReport();
        riskService.refresh(hazard);
        return new ReportCreated(report.getId(), hazard.getId(), true);
    }

    /** C-05: 오늘 00시(Asia/Seoul)부터 쓴 제보 수 (삭제한 제보도 셈) */
    private void requireDailyLimit(Long userId) {
        OffsetDateTime todayStart = LocalDate.now(SEOUL).atStartOfDay(SEOUL).toOffsetDateTime();
        if (reports.countByUserIdAndCreatedAtGreaterThanEqual(userId, todayStart) >= props.limits().reportsPerDay()) {
            throw new ApiException(ErrorCode.LIMIT);
        }
    }
}
