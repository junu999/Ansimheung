package com.slink.ansimheung.hazard;

import com.slink.ansimheung.common.error.ApiException;
import com.slink.ansimheung.common.error.ErrorCode;
import com.slink.ansimheung.common.geo.Geometries;
import com.slink.ansimheung.common.type.HazardStatus;
import com.slink.ansimheung.common.type.HazardType;
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
 * 위험 지점 = 지도 마커 1개 (hazards). 같은 위치(20m)·같은 유형 제보가 여기에 묶인다.
 * 점수: {@code baseScore = W×R×E×S} 만 저장, 시간대 T 는 조회할 때 곱한다 (risk.RiskService).
 * 카운트(reportCount·empathyCount)는 캐시값 — 제보·공감을 바꾸는 같은 트랜잭션에서 아래 메서드로 갱신.
 */
@Entity
@Table(name = "hazards")
public class Hazard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HazardType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_slot", nullable = false, length = 10)
    private TimeSlot timeSlot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private HazardStatus status;

    @Column(nullable = false)
    private boolean hidden;

    @Column(name = "hidden_reason", length = 200)
    private String hiddenReason;

    @Column(name = "base_score", nullable = false)
    private double baseScore;

    @Column(name = "empathy_count", nullable = false)
    private int empathyCount;

    @Column(name = "report_count", nullable = false)
    private int reportCount;

    /** C-09 가장 가까운 구간 1개 (25m 넘으면 null → 경로 계산에서 제외) */
    @Column(name = "edge_id")
    private Long edgeId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /** 최근성 R 기준 (마지막 제보·공감) */
    @Column(name = "last_activity_at", nullable = false)
    private OffsetDateTime lastActivityAt;

    /** P-11 개선완료 30일 후 지도에서 숨김 */
    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    protected Hazard() {
    }

    /** 새 위험 지점 (첫 제보와 함께 생성, 상태 = 접수). reportCount 는 {@link #addReport()} 로 올린다 */
    public static Hazard create(double lat, double lng, HazardType type, TimeSlot timeSlot) {
        Hazard h = new Hazard();
        h.location = Geometries.point(lat, lng);
        h.type = type;
        h.timeSlot = timeSlot;
        h.status = HazardStatus.RECEIVED;
        OffsetDateTime now = OffsetDateTime.now();
        h.createdAt = now;
        h.lastActivityAt = now;
        return h;
    }

    // ---------- 상태 ----------

    /** 허용된 전이만 (HazardStatus.allowedNext). 아니면 409 STATE_CONFLICT */
    public HazardStatus changeStatus(HazardStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new ApiException(ErrorCode.STATE_CONFLICT);
        }
        HazardStatus before = status;
        status = next;
        resolvedAt = next == HazardStatus.RESOLVED ? OffsetDateTime.now() : null;
        return before;
    }

    public void hide(String reason) {
        hidden = true;
        hiddenReason = reason;
    }

    public void unhide() {
        hidden = false;
        hiddenReason = null;
    }

    // ---------- 카운트 · 활동 ----------

    public void addReport() {
        reportCount++;
        touch();
    }

    public void removeReport() {
        reportCount = Math.max(0, reportCount - 1);
    }

    public void addEmpathy() {
        empathyCount++;
        touch();
    }

    public void removeEmpathy() {
        empathyCount = Math.max(0, empathyCount - 1);
    }

    private void touch() {
        lastActivityAt = OffsetDateTime.now();
    }

    // ---------- 위험도 (risk 모듈만 호출) ----------

    public void updateBaseScore(double baseScore) {
        this.baseScore = baseScore;
    }

    public void assignEdge(Long edgeId) {
        this.edgeId = edgeId;
    }

    // ---------- getter ----------

    public Long getId() {
        return id;
    }

    public Point getLocation() {
        return location;
    }

    public double getLat() {
        return Geometries.lat(location);
    }

    public double getLng() {
        return Geometries.lng(location);
    }

    public HazardType getType() {
        return type;
    }

    public TimeSlot getTimeSlot() {
        return timeSlot;
    }

    public HazardStatus getStatus() {
        return status;
    }

    public boolean isHidden() {
        return hidden;
    }

    public String getHiddenReason() {
        return hiddenReason;
    }

    public double getBaseScore() {
        return baseScore;
    }

    public int getEmpathyCount() {
        return empathyCount;
    }

    public int getReportCount() {
        return reportCount;
    }

    public Long getEdgeId() {
        return edgeId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getLastActivityAt() {
        return lastActivityAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }
}
