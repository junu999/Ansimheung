package com.slink.ansimheung.publiclayer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slink.ansimheung.common.type.PublicLayerKind;
import com.slink.ansimheung.publiclayer.dto.PublicLayerFeature;
import com.slink.ansimheung.publiclayer.dto.PublicLayerFeatureCollection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * F-MAP-06 공공데이터 레이어 (#34). 지도 표시 전용 — 위험점수·경로 계산에는 쓰지 않는다.
 * 데이터는 V2__public_layers.sql (scripts/build_public_layers.py 로 생성).
 * GeoJSON 변환을 PostGIS(ST_AsGeoJSON)에 맡기려고 JdbcTemplate 로 바로 조회한다.
 */
@RestController
public class PublicLayerController {

    private static final String SELECT =
            "SELECT id, kind, name, source, ST_AsGeoJSON(geom) AS geojson FROM public_layers";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PublicLayerController(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** @param kind 없으면 전체. 잘못된 값이면 400 VALIDATION (공통 핸들러) */
    @GetMapping("/api/public-layers")
    public PublicLayerFeatureCollection list(@RequestParam(required = false) PublicLayerKind kind) {
        List<PublicLayerFeature> features = kind == null
                ? jdbc.query(SELECT + " ORDER BY id", (rs, i) -> toFeature(rs.getLong("id"), rs.getString("kind"),
                        rs.getString("name"), rs.getString("source"), rs.getString("geojson")))
                : jdbc.query(SELECT + " WHERE kind = ? ORDER BY id", (rs, i) -> toFeature(rs.getLong("id"),
                        rs.getString("kind"), rs.getString("name"), rs.getString("source"), rs.getString("geojson")),
                        kind.name());
        return PublicLayerFeatureCollection.of(features);
    }

    private PublicLayerFeature toFeature(long id, String kind, String name, String source, String geojson) {
        try {
            return PublicLayerFeature.of(objectMapper.readTree(geojson),
                    new PublicLayerFeature.Properties(id, kind, name, source));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("ST_AsGeoJSON 결과를 읽을 수 없음: id=" + id, e);
        }
    }
}
