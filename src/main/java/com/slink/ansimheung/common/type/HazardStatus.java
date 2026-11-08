package com.slink.ansimheung.common.type;

import java.util.EnumSet;
import java.util.Set;

/**
 * 위험 지점 처리상태 (상세 기획서 8장). weight = 처리 가중치 S (C-03).
 * 숨김은 상태가 아니라 hazards.hidden 플래그.
 */
public enum HazardStatus {
    RECEIVED("접수", 1.0),
    CHECKING("확인중", 1.0),
    RESOLVED("개선완료", 0.3),
    REJECTED("반려", 0.0);

    private final String label;
    private final double weight;

    HazardStatus(String label, double weight) {
        this.label = label;
        this.weight = weight;
    }

    public String label() {
        return label;
    }

    /** 반려는 계산 대상이 아니라 0 (지도에서도 제외) */
    public double weight() {
        return weight;
    }

    /** 허용 전이: 접수 → 확인중/반려, 확인중 → 개선완료/반려, 개선완료·반려 → 없음 */
    public Set<HazardStatus> allowedNext() {
        return switch (this) {
            case RECEIVED -> EnumSet.of(CHECKING, REJECTED);
            case CHECKING -> EnumSet.of(RESOLVED, REJECTED);
            case RESOLVED, REJECTED -> EnumSet.noneOf(HazardStatus.class);
        };
    }

    public boolean canTransitionTo(HazardStatus next) {
        return allowedNext().contains(next);
    }

    /** 묶기 후보가 될 수 있는 상태 (P-01) */
    public boolean isOpen() {
        return this == RECEIVED || this == CHECKING;
    }
}
