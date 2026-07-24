package com.glassvue.domain.notification.entity;

import com.glassvue.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 회원의 알림 켜기/끄기 설정 (2026-07-24). 타입별 <b>opt-out</b> —
 * 행이 없으면 켜짐(기본 on)으로 본다. 그래서 <b>끈 것만</b> 행으로 남는다(모든 회원×타입을 미리 채우지 않는다).
 */
@Entity
@Getter
@Table(name = "member_notification_pref")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationPref extends BaseTimeEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "member_id", columnDefinition = "RAW(16)", nullable = false, updatable = false)
    private UUID memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private NotificationType type;

    @Column(nullable = false)
    private boolean enabled;

    private NotificationPref(UUID memberId, NotificationType type, boolean enabled) {
        this.memberId = memberId;
        this.type = type;
        this.enabled = enabled;
    }

    public static NotificationPref of(UUID memberId, NotificationType type, boolean enabled) {
        return new NotificationPref(memberId, type, enabled);
    }

    public void change(boolean enabled) {
        this.enabled = enabled;
    }
}
