package com.slink.ansimheung.admin.dto;

import java.util.List;
import java.util.Map;

/**
 * GET /api/admin/hazards 응답 = static/mock/admin-hazards.json.
 * counts 는 필터와 관계없는 전체 개수 (상태별은 숨김 제외, 숨김은 HIDDEN 에 따로).
 */
public record AdminHazardPage(Map<String, Long> counts, List<AdminHazardItem> content,
                              int page, int size, long totalElements, int totalPages) {
}
