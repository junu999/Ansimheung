package com.slink.ansimheung.admin.dto;

import com.slink.ansimheung.common.type.HazardStatus;

/** PATCH /api/admin/hazards/{id}/status 응답 */
public record StatusChangeResponse(Long id, HazardStatus status) {
}
