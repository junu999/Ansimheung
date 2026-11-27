package com.slink.ansimheung.hazard.dto;

import com.slink.ansimheung.common.type.HazardStatus;
import com.slink.ansimheung.common.type.HazardType;
import com.slink.ansimheung.common.type.MarkerLevel;
import com.slink.ansimheung.common.type.TimeSlot;

import java.time.OffsetDateTime;
import java.util.List;

/** GET /api/hazards 응답 = static/mock/hazards.json (GeoJSON FeatureCollection, 좌표는 [경도, 위도]) */
public record HazardFeatureCollection(String type, List<Feature> features) {

    public static HazardFeatureCollection of(List<Feature> features) {
        return new HazardFeatureCollection("FeatureCollection", features);
    }

    public record Feature(String type, Geometry geometry, Properties properties) {

        public static Feature of(double lat, double lng, Properties properties) {
            return new Feature("Feature", new Geometry("Point", new double[]{lng, lat}), properties);
        }
    }

    public record Geometry(String type, double[] coordinates) {
    }

    public record Properties(Long id, HazardType type, TimeSlot timeSlot, HazardStatus status, double score,
                             MarkerLevel level, int empathyCount, int reportCount, OffsetDateTime createdAt) {
    }
}
