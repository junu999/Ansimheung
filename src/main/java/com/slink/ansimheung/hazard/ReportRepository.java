package com.slink.ansimheung.hazard;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /** 지점의 제보 (삭제 제외, 오래된 순) — 상세 화면 */
    List<Report> findByHazardIdAndDeletedFalseOrderByCreatedAtAsc(Long hazardId);

    /** DUP_REPORT 판정 (같은 사람이 같은 지점에 이미 제보) */
    boolean existsByHazardIdAndUserIdAndDeletedFalse(Long hazardId, Long userId);

    /** C-05 일일 한도: since = 오늘 00시(Asia/Seoul) */
    long countByUserIdAndCreatedAtGreaterThanEqual(Long userId, OffsetDateTime since);
}
