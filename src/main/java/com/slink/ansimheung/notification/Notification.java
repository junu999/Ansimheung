package com.slink.ansimheung.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** 처리결과 알림 (notifications). 이번 제출 범위 제외(#28) — 테이블만 매핑해 둠 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "hazard_id")
    private Long hazardId;

    @Column(nullable = false, length = 200)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Notification() {
    }

    public static Notification of(Long userId, Long hazardId, String message) {
        Notification n = new Notification();
        n.userId = userId;
        n.hazardId = hazardId;
        n.message = message;
        n.createdAt = OffsetDateTime.now();
        return n;
    }

    public void markRead() {
        read = true;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getHazardId() {
        return hazardId;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRead() {
        return read;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
