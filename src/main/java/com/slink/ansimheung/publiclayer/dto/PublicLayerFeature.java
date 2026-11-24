package com.slink.ansimheung.publiclayer.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** geometry 는 PostGIS ST_AsGeoJSON 결과 그대로 ([경도, 위도] 순서) */
public record PublicLayerFeature(String type, JsonNode geometry, Properties properties) {

    public record Properties(Long id, String kind, String name, String source) {
    }

    public static PublicLayerFeature of(JsonNode geometry, Properties properties) {
        return new PublicLayerFeature("Feature", geometry, properties);
    }
}
