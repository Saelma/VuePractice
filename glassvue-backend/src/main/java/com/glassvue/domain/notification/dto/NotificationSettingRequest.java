package com.glassvue.domain.notification.dto;

import com.glassvue.domain.notification.entity.NotificationType;
import jakarta.validation.constraints.NotNull;

/** 알림 타입 하나의 켜기/끄기. 마이페이지 토글이 한 번에 하나씩 보낸다. */
public record NotificationSettingRequest(
        @NotNull NotificationType type,
        @NotNull Boolean enabled
) {
}
