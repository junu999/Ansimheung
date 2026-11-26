package com.slink.ansimheung.admin.dto;

import com.slink.ansimheung.common.type.HazardStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** PATCH /api/admin/hazards/{id}/status 요청 */
public record StatusChangeRequest(
        @NotNull(message = "처리 상태를 선택해 주세요") HazardStatus status,
        @Size(max = 200, message = "메모는 200자까지 쓸 수 있어요") String memo   // V-10
) {
}
