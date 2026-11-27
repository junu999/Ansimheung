package com.slink.ansimheung.hazard.dto;

/** GET /api/hazards/nearby 응답 — mock/hazards-nearby.json. 후보가 없으면 candidate = null */
public record NearbyCandidate(Candidate candidate) {

    /** distanceM = 제보 위치에서 지점까지 거리(m, 정수 반올림) */
    public record Candidate(Long id, long distanceM, int empathyCount, int reportCount) {
    }
}
