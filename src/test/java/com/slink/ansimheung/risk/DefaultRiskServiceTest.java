package com.slink.ansimheung.risk;

import com.slink.ansimheung.common.type.HazardStatus;
import com.slink.ansimheung.common.type.HazardType;
import com.slink.ansimheung.common.type.MarkerLevel;
import com.slink.ansimheung.common.type.TimeSlot;
import com.slink.ansimheung.hazard.Hazard;
import com.slink.ansimheung.hazard.HazardRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** 구간 배정 + baseScore 저장 (DB 필요). 그래프는 Flyway V3__walk_graph.sql 의 실제 데이터 */
@SpringBootTest
@Transactional
class DefaultRiskServiceTest {

    @Autowired RiskService riskService;
    @Autowired HazardRepository hazardRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    /** 구간 위의 점 (구간 길이의 중간). 다른 구간과 겹치지 않으면 이 구간이 가장 가깝다 */
    private Map<String, Object> pointOnEdge(long edgeId) {
        return jdbc.queryForMap("""
                SELECT ST_Y(p) AS lat, ST_X(p) AS lng
                FROM (SELECT ST_LineInterpolatePoint(geom, 0.5) AS p FROM edges WHERE id = ?) t
                """, edgeId);
    }

    private double distanceToEdgeM(long edgeId, double lat, double lng) {
        return jdbc.queryForObject("""
                SELECT ST_Distance(geom::geography, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography)
                FROM edges WHERE id = ?
                """, Double.class, lng, lat, edgeId);
    }

    @Test
    void 제보_1건_저장_후_refresh하면_base_score와_edge_id가_DB에_채워진다() {
        long target = jdbc.queryForObject("SELECT id FROM edges ORDER BY length_m DESC, id LIMIT 1", Long.class);
        Map<String, Object> p = pointOnEdge(target);
        double lat = (double) p.get("lat");
        double lng = (double) p.get("lng");

        Hazard h = Hazard.create(lat, lng, HazardType.ILLEGAL_PARKING, TimeSlot.MORNING);
        h.addReport();
        riskService.refresh(h);
        hazardRepository.save(h);
        em.flush();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT base_score, edge_id FROM hazards WHERE id = ?", h.getId());
        // 방금 생성: R=1.5(상한), 공감 0 → E=1.0, 접수 S=1.0 → 1.8 × 1.5
        assertThat((double) row.get("base_score")).isCloseTo(2.7, within(0.001));
        assertThat(row.get("edge_id")).isNotNull();
        assertThat(distanceToEdgeM((long) row.get("edge_id"), lat, lng)).isLessThan(0.01);
    }

    @Test
    void 반경_안에_구간이_여러_개여도_가장_가까운_1개만_배정된다() {
        // 학교 남동쪽 점: 25m 안에 구간이 2개 이상 있는 곳 (V3 기준 9.2m · 12.2m)
        double lat = 37.3405;
        double lng = 126.7436;
        var within25 = jdbc.queryForList("""
                SELECT ST_Distance(geom::geography, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography) AS d
                FROM edges ORDER BY d LIMIT 2
                """, Double.class, lng, lat);
        assertThat(within25).as("전제: 25m 안에 구간 2개 이상").hasSize(2).allMatch(d -> d <= 25);

        Hazard h = Hazard.create(lat, lng, HazardType.SPEEDING, TimeSlot.ALWAYS);
        riskService.refresh(h);

        assertThat(h.getEdgeId()).isNotNull();
        assertThat(distanceToEdgeM(h.getEdgeId(), lat, lng)).isCloseTo(within25.get(0), within(1e-6));
    }

    @Test
    void 길에서_25m를_넘으면_배정하지_않는다() {
        // 시범 지역 밖 먼 곳 (그래프가 없는 곳)
        Hazard h = Hazard.create(37.3500, 126.7600, HazardType.CONSTRUCTION, TimeSlot.NIGHT);
        riskService.refresh(h);

        assertThat(h.getEdgeId()).isNull();
        assertThat(h.getBaseScore()).isCloseTo(1.4 * 1.5, within(0.001));
    }

    @Test
    void 반려되면_refresh_후_0점() {
        long edge = jdbc.queryForObject("SELECT min(id) FROM edges", Long.class);
        Map<String, Object> p = pointOnEdge(edge);
        Hazard h = Hazard.create((double) p.get("lat"), (double) p.get("lng"), HazardType.SIDEWALK_GAP, TimeSlot.AFTERNOON);
        riskService.refresh(h);
        assertThat(h.getBaseScore()).isPositive();

        h.changeStatus(HazardStatus.REJECTED);
        riskService.refresh(h);

        assertThat(h.getBaseScore()).isZero();
    }

    @Test
    void 공감이_늘면_refresh_후_점수가_오른다() {
        Hazard h = Hazard.create(37.3405, 126.7436, HazardType.POOR_LIGHTING, TimeSlot.NIGHT);
        riskService.refresh(h);
        double before = h.getBaseScore();

        h.addEmpathy();
        h.addEmpathy();
        h.addEmpathy();
        riskService.refresh(h);

        assertThat(h.getBaseScore()).isGreaterThan(before);
    }

    @Test
    void scoreAt은_조회_시간대를_곱하고_levelOf는_기준값으로_색을_정한다() {
        Hazard h = Hazard.create(37.3405, 126.7436, HazardType.ILLEGAL_PARKING, TimeSlot.MORNING);
        riskService.refresh(h);   // 2.7

        double morning = riskService.scoreAt(h, Optional.of(TimeSlot.MORNING));
        double lunch = riskService.scoreAt(h, Optional.empty());
        assertThat(morning).isCloseTo(2.7 * 1.5, within(0.001));
        assertThat(lunch).isCloseTo(2.7, within(0.001));
        assertThat(riskService.levelOf(h, morning)).isEqualTo(MarkerLevel.HIGH);    // 4.05 ≥ 3.0
        assertThat(riskService.levelOf(h, lunch)).isEqualTo(MarkerLevel.MEDIUM);
    }
}
