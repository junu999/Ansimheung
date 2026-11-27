package com.slink.ansimheung.hazard;

import com.slink.ansimheung.common.type.HazardStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

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

    /** 경로 비용에 들어가는 지점 (#13): 구간이 배정됐고, 숨김 아니고, 반려 아님. 개선완료 30일 숨김은 호출하는 쪽에서 */
    List<Hazard> findByEdgeIdIsNotNullAndHiddenFalseAndStatusNot(HazardStatus status);

    /** 지도 조회 (#12): bbox(서,남,동,북) 안, 숨김·반려 제외. 개선완료 30일 숨김(P-11)은 호출하는 쪽에서 */
    @Query(value = """
            SELECT * FROM hazards
            WHERE hidden = false AND status <> 'REJECTED'
              AND ST_Intersects(location, ST_MakeEnvelope(:west, :south, :east, :north, 4326))
            ORDER BY created_at DESC, id DESC
            """, nativeQuery = true)
    List<Hazard> findVisibleInBbox(@Param("west") double west, @Param("south") double south,
                                   @Param("east") double east, @Param("north") double north);

    /**
     * 묶기 후보 (#25, P-01): 반경 radiusM 안 · 같은 유형 · statuses(접수/확인중) · 숨김 아님 중 가장 가까운 1곳.
     * 거리는 geography(미터). 개선완료 지점은 후보가 아니라서 근처에 제보하면 새 지점(재발생, P-02)
     */
    @Query(value = """
            SELECT * FROM hazards
            WHERE hidden = false AND type = :type AND status IN (:statuses)
              AND ST_DWithin(location::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusM)
            ORDER BY ST_Distance(location::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography), id
            LIMIT 1
            """, nativeQuery = true)
    Optional<Hazard> findMergeCandidate(@Param("lat") double lat, @Param("lng") double lng,
                                        @Param("type") String type, @Param("statuses") List<String> statuses,
                                        @Param("radiusM") double radiusM);
}
