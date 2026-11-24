package com.slink.ansimheung.hazard;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

    Optional<Photo> findByReportId(Long reportId);

    List<Photo> findByReportIdIn(Collection<Long> reportIds);
}
