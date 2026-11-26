package com.slink.ansimheung.notification.dto;

import java.util.List;

/** GET /api/me/notifications 응답 = static/mock/me-notifications.json (최신순) */
public record NotificationList(long unreadCount, List<NotificationItem> items) {
}
