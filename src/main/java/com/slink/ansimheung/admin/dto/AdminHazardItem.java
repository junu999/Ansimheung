package com.slink.ansimheung.admin.dto;

import com.slink.ansimheung.common.type.HazardStatus;
import com.slink.ansimheung.common.type.HazardType;
import com.slink.ansimheung.common.type.MarkerLevel;
import com.slink.ansimheung.common.type.TimeSlot;

import java.time.OffsetDateTime;

/** 관리자 목록 한 줄 = static/mock/admin-hazards.json content[] */
public record AdminHazardItem(Long id, HazardType type, TimeSlot timeSlot, HazardStatus status, double score,
                              MarkerLevel level, int empathyCount, int reportCount, boolean hidden,
                              OffsetDateTime createdAt, OffsetDateTime lastActivityAt) {
}
