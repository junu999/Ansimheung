package com.slink.ansimheung.hazard;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {

    List<StatusHistory> findByHazardIdOrderByCreatedAtAsc(Long hazardId);
}
