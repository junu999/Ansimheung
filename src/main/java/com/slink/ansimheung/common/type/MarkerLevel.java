package com.slink.ansimheung.common.type;

/** 지도 마커 색 (F-MAP-05, C-08) */
public enum MarkerLevel {
    HIGH,      // 빨강: 점수 ≥ 기준값
    MEDIUM,    // 주황
    RESOLVED;  // 회색: 개선완료

    public static MarkerLevel of(HazardStatus status, double score, double highThreshold) {
        if (status == HazardStatus.RESOLVED) return RESOLVED;
        return score >= highThreshold ? HIGH : MEDIUM;
    }
}
