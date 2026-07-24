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
 * 인앱 알림 한 건 (2026-07-24). 회원 한 명에게 쌓인다(알림함).
 *
 * <p>내용은 <b>스냅샷</b>이다 — 주문·상품이 나중에 바뀌어도 알림은 그때 문구 그대로 남는다.
 * {@code link} 는 클릭 시 이동 경로(예: {@code /orders/{id}})라 화면이 라우팅 규칙을 몰라도 된다.
 */
@Entity
@Getter
@Table(name = "notification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "member_id", columnDefinition = "RAW(16)", nullable = false, updatable = false)
    private UUID memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private NotificationType type;

    @Column(nullable = false, length = 200, updatable = false)
    private String title;

    @Column(nullable = false, length = 1000, updatable = false)
    private String message;

    @Column(length = 500, updatable = false)
    private String link;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    private Notification(UUID memberId, NotificationType type, String title, String message, String link) {
        this.memberId = memberId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.link = link;
        this.read = false;
    }

    public static Notification of(UUID memberId, NotificationType type, String title, String message, String link) {
        return new Notification(memberId, type, title, message, link);
    }

    /** 읽음 처리(멱등). 이미 읽었으면 아무 일도 안 한다. */
    public void markRead() {
        this.read = true;
    }
}
