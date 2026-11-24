package com.slink.ansimheung.hazard;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** 공감 복합키 (user_id, hazard_id) — 1인 1지점 1회를 DB 가 막는다 (P-03) */
@Embeddable
public class EmpathyId implements Serializable {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "hazard_id")
    private Long hazardId;

    protected EmpathyId() {
    }

    public EmpathyId(Long userId, Long hazardId) {
        this.userId = userId;
        this.hazardId = hazardId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getHazardId() {
        return hazardId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmpathyId other)) return false;
        return Objects.equals(userId, other.userId) && Objects.equals(hazardId, other.hazardId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, hazardId);
    }
}
