package com.slink.ansimheung.hazard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 제보 사진 (photos, 제보 1건당 0~1장). 파일은 uploads/ 아래 UUID 이름 — EXIF 제거 후 저장 (P-08).
 * 관리자 승인 전(approved=false)에는 제보자 본인과 관리자만 볼 수 있다.
 */
@Entity
@Table(name = "photos")
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "file_path", nullable = false, length = 255)
    private String filePath;

    /** image/jpeg | image/png */
    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType;

    @Column(nullable = false)
    private boolean approved;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Photo() {
    }

    public static Photo create(Long reportId, String filePath, String contentType) {
        Photo p = new Photo();
        p.reportId = reportId;
        p.filePath = filePath;
        p.contentType = contentType;
        p.createdAt = OffsetDateTime.now();
        return p;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public Long getId() {
        return id;
    }

    public Long getReportId() {
        return reportId;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getContentType() {
        return contentType;
    }

    public boolean isApproved() {
        return approved;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
