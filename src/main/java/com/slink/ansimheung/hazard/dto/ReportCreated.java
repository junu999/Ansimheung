package com.slink.ansimheung.hazard.dto;

/** POST /api/reports 201 응답 — mock/reports-created.json. 새 지점이면 merged = false */
public record ReportCreated(Long reportId, Long hazardId, boolean merged) {
}
