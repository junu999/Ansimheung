package com.slink.ansimheung.hazard;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;

public interface EmpathyRepository extends JpaRepository<Empathy, EmpathyId> {

    /** C-06 일일 한도: since = 오늘 00시(Asia/Seoul) */
    long countByIdUserIdAndCreatedAtGreaterThanEqual(Long userId, OffsetDateTime since);
}
