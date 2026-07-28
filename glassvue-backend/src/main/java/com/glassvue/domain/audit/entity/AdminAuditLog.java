package com.glassvue.domain.audit.entity;

import com.glassvue.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 관리자 조작 감사 이력 한 건. <b>불변 append-only</b> — 한 번 남기면 수정하지 않는다.
 *
 * <p>행위자·대상 식별자는 <b>FK 없는 느슨한 UUID</b>다(도메인 경계 — audit 은 member 를 밖에서 가리킨다).
 * 그래서 대상이 나중에 탈퇴·개명·강등돼도 이력이 깨지지 않도록, 그 시점의 이름·loginId 를
 * <b>스냅샷</b>으로 함께 박아 둔다({@code actorName}, {@code targetLogin}).
 */
@Getter
@Entity
@Table(name = "admin_audit_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditLog extends BaseTimeEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private AuditAction action;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "actor_id", columnDefinition = "RAW(16)", nullable = false, updatable = false)
    private UUID actorId;

    /** 조작 시점 행위자 닉네임 스냅샷(토큰 클레임에서 온다). member.nickname 과 같은 50자. */
    @Column(name = "actor_name", nullable = false, updatable = false, length = 50)
    private String actorName;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "target_id", columnDefinition = "RAW(16)", nullable = false, updatable = false)
    private UUID targetId;

    /** 조작 시점 대상 loginId 스냅샷. member.login_id 와 같은 50자. */
    @Column(name = "target_login", nullable = false, updatable = false, length = 50)
    private String targetLogin;

    /** 부가 설명. 역할변경이면 {@code "USER → ADMIN"} 같은 전/후. 정지·해제는 비어 있다. */
    @Column(name = "detail", updatable = false, length = 1000)
    private String detail;

    @Builder
    private AdminAuditLog(AuditAction action, UUID actorId, String actorName,
                          UUID targetId, String targetLogin, String detail) {
        this.action = action;
        this.actorId = actorId;
        this.actorName = actorName;
        this.targetId = targetId;
        this.targetLogin = targetLogin;
        this.detail = detail;
    }
}
