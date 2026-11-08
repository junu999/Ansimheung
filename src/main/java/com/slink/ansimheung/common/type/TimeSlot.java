package com.slink.ansimheung.common.type;

import java.time.LocalTime;
import java.util.Optional;

/**
 * 시간대 4종 (P-07, C-04). 등교 07–09 · 하교 14–18 · 야간 18–07 · 상시.
 * 09–14시는 어느 시간대에도 속하지 않는다 → 시간대 가중치는 "상시" 제보만 1.2, 나머지 1.0.
 */
public enum TimeSlot {
    MORNING("등교"),
    AFTERNOON("하교"),
    NIGHT("야간"),
    ALWAYS("상시");

    private final String label;

    TimeSlot(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 조회 시각 → 시간대. 09–14시는 비어 있음 */
    public static Optional<TimeSlot> of(LocalTime time) {
        int h = time.getHour();
        if (h >= 7 && h < 9) return Optional.of(MORNING);
        if (h >= 14 && h < 18) return Optional.of(AFTERNOON);
        if (h >= 18 || h < 7) return Optional.of(NIGHT);
        return Optional.empty();
    }

    /** 조회 파라미터로 쓸 수 있는 값인지 (상시는 조회 시간대가 될 수 없음, V-08) */
    public boolean isQueryable() {
        return this != ALWAYS;
    }
}
