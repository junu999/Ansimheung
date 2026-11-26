package com.slink.ansimheung.notification;

import com.slink.ansimheung.common.error.ApiException;
import com.slink.ansimheung.common.error.ErrorCode;
import com.slink.ansimheung.common.type.HazardStatus;
import com.slink.ansimheung.hazard.Hazard;
import com.slink.ansimheung.hazard.Report;
import com.slink.ansimheung.hazard.ReportRepository;
import com.slink.ansimheung.notification.dto.NotificationItem;
import com.slink.ansimheung.notification.dto.NotificationList;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** 처리결과 알림 (#35, F-NOTI-01 · F-NOTI-02 목록·읽음만) */
@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final ReportRepository reports;

    public NotificationService(NotificationRepository notifications, ReportRepository reports) {
        this.notifications = notifications;
        this.reports = reports;
    }

    /**
     * 관리자 상태 변경 직후 같은 트랜잭션에서 호출 ({@code AdminService.changeStatus}).
     * 받는 사람 = 삭제 안 된 제보의 작성자 (DB 제약상 1인 1제보지만 중복 제거). 작성자 없는 제보(탈퇴·시드)는 건너뜀.
     */
    @Transactional
    public void notifyStatusChanged(Hazard hazard, HazardStatus next) {
        String message = message(next);
        reports.findByHazardIdAndDeletedFalseOrderByCreatedAtAsc(hazard.getId()).stream()
                .map(Report::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(userId -> notifications.save(Notification.of(userId, hazard.getId(), message)));
    }

    @Transactional(readOnly = true)
    public NotificationList list(Long userId) {
        List<NotificationItem> items = notifications.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(n -> new NotificationItem(n.getId(), n.getHazardId(), n.getMessage(), n.isRead(), n.getCreatedAt()))
                .toList();
        return new NotificationList(items.stream().filter(n -> !n.read()).count(), items);
    }

    /** 본인 알림만. 남의 것·없는 것은 존재를 드러내지 않도록 둘 다 NOT_FOUND. 이미 읽었으면 그대로 */
    @Transactional
    public void markRead(Long userId, Long id) {
        Notification n = notifications.findById(id)
                .filter(found -> found.getUserId().equals(userId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        n.markRead();
    }

    /** "제보하신 지점이 '확인중'으로 바뀌었어요" — 받침에 따라 으로/로 (ㄹ 받침은 '로') */
    static String message(HazardStatus status) {
        String label = status.label();
        return "제보하신 지점이 '" + label + "'" + (needsEuro(label) ? "으로" : "로") + " 바뀌었어요";
    }

    private static boolean needsEuro(String word) {
        char last = word.charAt(word.length() - 1);
        if (last < '가' || last > '힣') {
            return false;
        }
        int jong = (last - '가') % 28;
        return jong != 0 && jong != 8;   // 8 = ㄹ
    }
}
