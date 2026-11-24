package com.slink.ansimheung.route.dto;

import com.slink.ansimheung.common.type.TimeSlot;
import jakarta.validation.constraints.NotNull;

/**
 * POST /api/routes 요청. 출발지 좌표는 계산에만 쓰고 저장·로그하지 않는다 (P-12) — toString 도 좌표를 숨긴다.
 * @param timeSlot 선택. 없으면 지금 시각의 시간대 (09–14시는 없음). ALWAYS 불가 (V-08)
 */
public record RouteRequest(
        @NotNull(message = "위치를 선택해 주세요") Double fromLat,
        @NotNull(message = "위치를 선택해 주세요") Double fromLng,
        TimeSlot timeSlot
) {
    @Override
    public String toString() {
        return "RouteRequest[출발지 비공개, timeSlot=" + timeSlot + "]";
    }
}
