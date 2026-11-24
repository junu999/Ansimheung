package com.slink.ansimheung.hazard;

import com.slink.ansimheung.common.geo.Geometries;
import com.slink.ansimheung.common.type.TimeSlot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Point;

import java.time.OffsetDateTime;

/**
 * 제보 1건 (reports). 반드시 위험 지점 하나에 속한다.
 * 연관관계 대신 외래키 id 만 들고 있다 (지연 로딩 문제 없이 단순하게 — 필요한 조회는 리포지토리 쿼리로).
 */
@Entity
@Table(name = "reports")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hazard_id", nullable = false)
    private Long hazardId;

    /** 탈퇴 시 null (P-11) */
    @Column(name = "user_id")
    private Long userId;

    /** 제보자가 찍은 원래 위치 (묶였을 때 지점 위치와 다를 수 있음) */
    @Column(nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point location;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_slot", nullable = false, length = 10)
    private TimeSlot timeSlot;

    @Column(length = 200)
    private String description;

    /** P-09 소프트 삭제 */
    @Column(nullable = false)
    private boolean deleted;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Report() {
    }

    public static Report create(Long hazardId, Long userId, double lat, double lng, TimeSlot timeSlot, String description) {
        Report r = new Report();
        r.hazardId = hazardId;
        r.userId = userId;
        r.location = Geometries.point(lat, lng);
        r.timeSlot = timeSlot;
        r.description = description;
        r.createdAt = OffsetDateTime.now();
        return r;
    }

    public void edit(String description, TimeSlot timeSlot) {
        if (description != null) this.description = description;
        if (timeSlot != null) this.timeSlot = timeSlot;
    }

    public void delete() {
        deleted = true;
    }

    public Long getId() {
        return id;
    }

    public Long getHazardId() {
        return hazardId;
    }

    public Long getUserId() {
        return userId;
    }

    public Point getLocation() {
        return location;
    }

    public TimeSlot getTimeSlot() {
        return timeSlot;
    }

    public String getDescription() {
        return description;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
