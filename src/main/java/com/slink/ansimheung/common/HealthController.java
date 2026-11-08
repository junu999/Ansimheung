package com.slink.ansimheung.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 개발 환경 확인용 API.
 * GET /api/health → 서버 · DB · PostGIS 가 모두 연결됐는지 한 번에 확인합니다.
 */
@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("server", "ok");
        try {
            result.put("database", jdbcTemplate.queryForObject("SELECT version()", String.class));
            result.put("postgis", jdbcTemplate.queryForObject("SELECT PostGIS_Lib_Version()", String.class));
            // 공간 함수가 실제로 동작하는지: 정왕초에서 동쪽으로 약 89m 떨어진 점까지 거리(m)
            result.put("postgisDistanceTestM", jdbcTemplate.queryForObject(
                    "SELECT ROUND(ST_Distance(ST_MakePoint(126.7430, 37.3408)::geography, "
                            + "ST_MakePoint(126.7440, 37.3408)::geography))", Long.class));
        } catch (Exception e) {
            result.put("database", "error: " + e.getMessage());
        }
        return result;
    }
}
