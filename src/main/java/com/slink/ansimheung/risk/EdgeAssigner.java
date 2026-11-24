package com.slink.ansimheung.risk;

import com.slink.ansimheung.config.AnsimheungProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 위험 지점 → 가장 가까운 구간 1개 (C-09, 25m 이내). 넘으면 배정 없음 → 지도엔 보이지만 경로 비용엔 영향 없음.
 * 반경 안 구간에 모두 더하지 않는다 — 교차로 모퉁이 위험 하나가 구간 4개에 잡혀 중복 계산됨 (docs/domain.md 7장).
 * 거리는 geography(m) 기준으로 비교한다. geometry {@code <->} 는 도(°) 단위라 위도 37°에서 동서·남북 거리가 달라진다.
 */
@Component
public class EdgeAssigner {

    private static final String NEAREST_EDGE = """
            SELECT id FROM edges
            WHERE ST_DWithin(geom::geography, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)
            ORDER BY ST_Distance(geom::geography, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography), id
            LIMIT 1
            """;

    private final JdbcTemplate jdbc;
    private final double maxM;

    public EdgeAssigner(JdbcTemplate jdbc, AnsimheungProperties props) {
        this.jdbc = jdbc;
        this.maxM = props.route().edgeAssignMaxM();
    }

    public Optional<Long> nearestEdge(double lat, double lng) {
        return jdbc.queryForList(NEAREST_EDGE, Long.class, lng, lat, maxM, lng, lat).stream().findFirst();
    }
}
