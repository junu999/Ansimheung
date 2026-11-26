package com.slink.ansimheung.hazard.dto;

import com.slink.ansimheung.common.type.HazardStatus;
import com.slink.ansimheung.common.type.HazardType;
import com.slink.ansimheung.common.type.MarkerLevel;
import com.slink.ansimheung.common.type.TimeSlot;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * GET /api/hazards/{id} 응답 = static/mock/hazard-detail.json.
 * 공개 화면이라 작성자·관리자 정보는 넣지 않는다 (관리자 상세는 /api/admin/hazards/{id}).
 */
public record HazardDetail(Long id, double lat, double lng, HazardType type, TimeSlot timeSlot, HazardStatus status,
                           double score, MarkerLevel level, int empathyCount, int reportCount,
                           boolean myEmpathy, boolean myReported, List<ReportItem> reports, List<PhotoItem> photos,
                           List<HistoryItem> history, OffsetDateTime createdAt, OffsetDateTime lastActivityAt) {

    public record ReportItem(Long id, String description, OffsetDateTime createdAt) {
    }

    /** 승인된 사진만. 파일은 GET /api/photos/{id} (#24) */
    public record PhotoItem(Long id, boolean approved) {
    }

    public record HistoryItem(HazardStatus fromStatus, HazardStatus toStatus, String memo, OffsetDateTime createdAt) {
    }
}
