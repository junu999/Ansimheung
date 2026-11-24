package com.slink.ansimheung.publiclayer.dto;

import java.util.List;

/** GET /api/public-layers 응답 (GeoJSON FeatureCollection) — static/mock/public-layers.json 과 같은 모양 */
public record PublicLayerFeatureCollection(String type, List<PublicLayerFeature> features) {

    public static PublicLayerFeatureCollection of(List<PublicLayerFeature> features) {
        return new PublicLayerFeatureCollection("FeatureCollection", features);
    }
}
