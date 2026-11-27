package com.slink.ansimheung.hazard;

import com.slink.ansimheung.common.error.ApiException;
import com.slink.ansimheung.common.error.ErrorCode;
import com.slink.ansimheung.common.type.HazardStatus;
import com.slink.ansimheung.common.type.HazardType;
import com.slink.ansimheung.common.type.TimeSlot;
import com.slink.ansimheung.config.AnsimheungProperties;
import com.slink.ansimheung.hazard.dto.HazardDetail;
import com.slink.ansimheung.hazard.dto.HazardFeatureCollection;
import com.slink.ansimheung.risk.RiskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 지도 조회 · 공개 상세 (#12, F-MAP-02 · F-MAP-07). 점수 = base_score × T(조회 시간대) */
@Service
public class HazardService {

    private final HazardRepository hazards;
    private final ReportRepository reports;
    private final PhotoRepository photos;
    private final StatusHistoryRepository histories;
    private final EmpathyRepository empathies;
    private final RiskService riskService;
    private final AnsimheungProperties props;

    public HazardService(HazardRepository hazards, ReportRepository reports, PhotoRepository photos,
                         StatusHistoryRepository histories, EmpathyRepository empathies, RiskService riskService,
                         AnsimheungProperties props) {
        this.hazards = hazards;
        this.reports = reports;
        this.photos = photos;
        this.histories = histories;
        this.empathies = empathies;
        this.riskService = riskService;
        this.props = props;
    }

    /** @param types 비어 있으면 전체 */
    @Transactional(readOnly = true)
    public HazardFeatureCollection list(double[] bbox, Set<HazardType> types, Optional<TimeSlot> querySlot) {
        OffsetDateTime hideBefore = OffsetDateTime.now().minusDays(props.hazard().resolvedHideDays());
        List<HazardFeatureCollection.Feature> features = hazards.findVisibleInBbox(bbox[0], bbox[1], bbox[2], bbox[3])
                .stream()
                .filter(h -> types.isEmpty() || types.contains(h.getType()))
                .filter(h -> h.getResolvedAt() == null || !h.getResolvedAt().isBefore(hideBefore))   // P-11
                .map(h -> {
                    double score = riskService.scoreAt(h, querySlot);
                    return HazardFeatureCollection.Feature.of(h.getLat(), h.getLng(),
                            new HazardFeatureCollection.Properties(h.getId(), h.getType(), h.getTimeSlot(),
                                    h.getStatus(), round1(score), riskService.levelOf(h, score),
                                    h.getEmpathyCount(), h.getReportCount(), h.getCreatedAt()));
                })
                .toList();
        return HazardFeatureCollection.of(features);
    }

    /** 숨김·반려는 NOT_FOUND. userId 가 null(비로그인)이면 myEmpathy·myReported = false */
    @Transactional(readOnly = true)
    public HazardDetail detail(Long id, Long userId, Optional<TimeSlot> querySlot) {
        Hazard h = hazards.findById(id)
                .filter(found -> !found.isHidden() && found.getStatus() != HazardStatus.REJECTED)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        List<Report> rs = reports.findByHazardIdAndDeletedFalseOrderByCreatedAtAsc(id);

        List<HazardDetail.ReportItem> reportItems = rs.stream()
                .map(r -> new HazardDetail.ReportItem(r.getId(), r.getDescription(), r.getCreatedAt()))
                .toList();
        List<HazardDetail.PhotoItem> photoItems = rs.isEmpty() ? List.of()
                : photos.findByReportIdIn(rs.stream().map(Report::getId).toList()).stream()
                .filter(Photo::isApproved)                                     // P-08 승인 전에는 공개 안 함
                .map(p -> new HazardDetail.PhotoItem(p.getId(), true))
                .toList();
        List<HazardDetail.HistoryItem> historyItems = histories.findByHazardIdOrderByCreatedAtAsc(id).stream()
                .sorted(Comparator.comparing(StatusHistory::getCreatedAt).thenComparing(StatusHistory::getId))
                .map(s -> new HazardDetail.HistoryItem(s.getFromStatus(), s.getToStatus(), s.getMemo(), s.getCreatedAt()))
                .toList();

        boolean myEmpathy = userId != null && empathies.existsById(new EmpathyId(userId, id));
        boolean myReported = userId != null && reports.existsByHazardIdAndUserIdAndDeletedFalse(id, userId);
        double score = riskService.scoreAt(h, querySlot);
        return new HazardDetail(h.getId(), h.getLat(), h.getLng(), h.getType(), h.getTimeSlot(), h.getStatus(),
                round1(score), riskService.levelOf(h, score), h.getEmpathyCount(), h.getReportCount(),
                myEmpathy, myReported, reportItems, photoItems, historyItems, h.getCreatedAt(), h.getLastActivityAt());
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
