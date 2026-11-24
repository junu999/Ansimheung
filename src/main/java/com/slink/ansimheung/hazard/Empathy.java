package com.slink.ansimheung.hazard;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** 공감 (empathies). 추가·삭제와 같은 트랜잭션에서 Hazard.addEmpathy()/removeEmpathy() + 점수 재계산 */
@Entity
@Table(name = "empathies")
public class Empathy {

    @EmbeddedId
    private EmpathyId id;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Empathy() {
    }

    public static Empathy of(Long userId, Long hazardId) {
        Empathy e = new Empathy();
        e.id = new EmpathyId(userId, hazardId);
        e.createdAt = OffsetDateTime.now();
        return e;
    }

    public EmpathyId getId() {
        return id;
    }

    public Long getUserId() {
        return id.getUserId();
    }

    public Long getHazardId() {
        return id.getHazardId();
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
