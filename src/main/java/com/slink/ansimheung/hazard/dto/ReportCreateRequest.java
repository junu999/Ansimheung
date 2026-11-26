package com.slink.ansimheung.hazard.dto;

import com.slink.ansimheung.common.type.HazardType;
import com.slink.ansimheung.common.type.TimeSlot;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /api/reports multipart 필드. 메시지는 docs/domain.md V-06 ~ V-09 그대로.
 * 설명은 앞뒤 공백을 지우고, 비면 null. photo(#24)·mergeHazardId(#25)는 아직 받지 않는다.
 */
public record ReportCreateRequest(
        @NotNull(message = LOCATION) Double lat,
        @NotNull(message = LOCATION) Double lng,
        @NotNull(message = TYPE) HazardType type,
        @NotNull(message = TIME_SLOT) TimeSlot timeSlot,
        @Size(max = 200, message = DESCRIPTION) String description
) {
    static final String LOCATION = "위치를 선택해 주세요";               // V-06
    static final String TYPE = "위험 유형을 선택해 주세요";               // V-07
    static final String TIME_SLOT = "시간대를 선택해 주세요";             // V-08
    static final String DESCRIPTION = "설명은 200자까지 쓸 수 있어요";    // V-09

    public ReportCreateRequest {
        description = description == null || description.isBlank() ? null : description.strip();
    }
}
