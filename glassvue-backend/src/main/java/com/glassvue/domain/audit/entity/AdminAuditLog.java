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

    /**
     * 조작 시점 대상 loginId 스냅샷. member.login_id 와 같은 50자.
     *
     * <p>🔴 <b>2026-08-10 부터 nullable 이다</b>(V44). 처음엔 NOT NULL 이었고 그게 맞았다 —
     * 대상이 <b>회원 그 자체</b>인 조작(정지·역할변경·삭제)뿐이었고, 그때는 대상이 반드시 존재했다.
     *
     * <p>콘텐츠 조치(리뷰 숨김·주문 취소)가 생기면서 전제가 깨졌다: <b>콘텐츠는 작성자보다 오래 산다.</b>
     * F-1 이 탈퇴 시 주문·리뷰를 남기므로, 그 행이 가리키는 회원은 <b>이미 없을 수 있다.</b>
     * NOT NULL 을 유지하면 «탈퇴 회원의 리뷰를 숨김» 같은 조작이 <b>통째로 실패</b>한다.
     *
     * <p>⚠ {@code null} 은 «조작 시점에 대상 회원이 이미 없었다» 는 <b>사실</b>이다.
     * 닉네임이나 {@code "(탈퇴)"} 로 메우지 않는다 — 이 열로 조회하는 사람이 없는 계정을 찾게 된다.
     */
    @Column(name = "target_login", updatable = false, length = 50)
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
