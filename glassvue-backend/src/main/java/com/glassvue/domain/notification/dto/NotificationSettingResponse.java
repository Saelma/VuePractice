package com.glassvue.domain.notification.dto;

import com.glassvue.domain.notification.entity.NotificationType;

/** 알림 타입별 켜짐 여부 — 모든 타입을 항상 내려준다(끈 것만 저장하지만 화면엔 전부 보여야 토글할 수 있다). */
public record NotificationSettingResponse(NotificationType type, String label, boolean enabled) {
}
