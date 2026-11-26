package com.slink.ansimheung.notification.dto;

import java.time.OffsetDateTime;

/** 알림 한 건 = static/mock/me-notifications.json items[] */
public record NotificationItem(Long id, Long hazardId, String message, boolean read, OffsetDateTime createdAt) {
}
