package com.slink.ansimheung.notification;

import com.slink.ansimheung.auth.LoginUser;
import com.slink.ansimheung.notification.dto.NotificationList;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** /api/me/notifications — 회원만 (SecurityConfig /api/me/**) */
@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/api/me/notifications")
    public NotificationList list(@AuthenticationPrincipal LoginUser user) {
        return notificationService.list(user.id());
    }

    @PatchMapping("/api/me/notifications/{id}/read")
    public ResponseEntity<Void> read(@AuthenticationPrincipal LoginUser user, @PathVariable Long id) {
        notificationService.markRead(user.id(), id);
        return ResponseEntity.noContent().build();
    }
}
