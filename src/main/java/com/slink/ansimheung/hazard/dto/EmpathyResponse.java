package com.slink.ansimheung.hazard.dto;

/** POST·DELETE /api/hazards/{id}/empathy 응답 — mock/empathy-on.json · empathy-off.json */
public record EmpathyResponse(int empathyCount, boolean myEmpathy) {
}
