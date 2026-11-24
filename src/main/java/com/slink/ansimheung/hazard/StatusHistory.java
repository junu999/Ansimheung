package com.slink.ansimheung.hazard;

import com.slink.ansimheung.common.type.HazardStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** 처리 이력 (status_histories). 지점 생성 시 (null → RECEIVED) 1건, 상태 변경마다 1건 */
@Entity
@Table(name = "status_histories")
public class StatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hazard_id", nullable = false)
    private Long hazardId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 10)
    private HazardStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 10)
    private HazardStatus toStatus;

    /** 시스템이 만든 이력은 null */
    @Column(name = "admin_id")
    private Long adminId;

    @Column(length = 200)
    private String memo;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected StatusHistory() {
    }

    public static StatusHistory of(Long hazardId, HazardStatus from, HazardStatus to, Long adminId, String memo) {
        StatusHistory h = new StatusHistory();
        h.hazardId = hazardId;
        h.fromStatus = from;
        h.toStatus = to;
        h.adminId = adminId;
        h.memo = memo;
        h.createdAt = OffsetDateTime.now();
        return h;
    }

    public Long getId() {
        return id;
    }

    public Long getHazardId() {
        return hazardId;
    }

    public HazardStatus getFromStatus() {
        return fromStatus;
    }

    public HazardStatus getToStatus() {
        return toStatus;
    }

    public Long getAdminId() {
        return adminId;
    }

    public String getMemo() {
        return memo;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
