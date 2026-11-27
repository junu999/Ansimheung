package com.slink.ansimheung.hazard.dto;

import com.slink.ansimheung.common.type.HazardType;
import jakarta.validation.constraints.NotNull;

/** GET /api/hazards/nearby?lat=&lng=&type= — 메시지는 V-06·V-07 (제보 등록과 같음) */
public record NearbyQuery(
        @NotNull(message = ReportCreateRequest.LOCATION) Double lat,
        @NotNull(message = ReportCreateRequest.LOCATION) Double lng,
        @NotNull(message = ReportCreateRequest.TYPE) HazardType type
) {
}
