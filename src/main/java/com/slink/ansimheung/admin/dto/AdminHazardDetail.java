package com.slink.ansimheung.admin.dto;

import com.slink.ansimheung.common.type.HazardStatus;
import com.slink.ansimheung.common.type.HazardType;
import com.slink.ansimheung.common.type.TimeSlot;

import java.time.OffsetDateTime;
import java.util.List;

/** GET /api/admin/hazards/{id} 응답 = static/mock/admin-hazard-detail.json (작성자 닉네임은 관리자 화면에만) */
public record AdminHazardDetail(Long id, double lat, double lng, HazardType type, TimeSlot timeSlot,
                                HazardStatus status, boolean hidden, double score, int empathyCount, int reportCount,
                                List<HazardStatus> allowedNextStatuses, List<ReportItem> reports,
                                List<PhotoItem> photos, List<HistoryItem> history) {

    /** authorNickname: 탈퇴했거나 작성자가 없으면 null */
    public record ReportItem(Long id, String authorNickname, String description, OffsetDateTime createdAt) {
    }

    /** 미승인 사진도 포함 (관리자). 파일은 GET /api/photos/{id} */
    public record PhotoItem(Long id, boolean approved) {
    }

    /** adminNickname: 시스템이 만든 이력(최초 접수)은 null */
    public record HistoryItem(HazardStatus fromStatus, HazardStatus toStatus, String memo, String adminNickname,
                              OffsetDateTime createdAt) {
    }
}
