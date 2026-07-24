package com.glassvue.domain.notification.dto;

import com.glassvue.domain.notification.entity.Notification;
import com.glassvue.domain.notification.entity.NotificationType;
import java.time.Instant;
import java.util.UUID;

/** 알림 한 건 — 목록·SSE 푸시에 같은 모양으로 실린다(화면이 새 알림을 받아 바로 목록에 넣을 수 있게). */
public record NotificationResponse(
        UUID id,
        NotificationType type,
        String typeLabel,
        String title,
        String message,
        String link,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getType().label(),
                n.getTitle(), n.getMessage(), n.getLink(), n.isRead(), n.getCreatedAt());
    }
}
