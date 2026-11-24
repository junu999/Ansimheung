package com.slink.ansimheung.hazard;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 위험 지점 조회. 공간 검색(bbox·반경 20m·가장 가까운 구간)은 각 기능 이슈에서
 * PostGIS 네이티브 쿼리로 추가한다. 예:
 * <pre>
 * {@code @Query(value = "SELECT * FROM hazards WHERE ST_Intersects(location, ST_MakeEnvelope(:w, :s, :e, :n, 4326))", nativeQuery = true)}
 * </pre>
 * 거리(m)는 {@code ST_DWithin(location::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusM)}.
 * ⚠️ ST_MakePoint 는 (경도, 위도) 순서.
 */
public interface HazardRepository extends JpaRepository<Hazard, Long> {
}
