package com.glassvue.domain.member.service.command;

import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.domain.audit.event.AdminActionEvent;
import com.glassvue.domain.member.dto.AdminMemberResponse;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.security.AuthUser;
import com.glassvue.global.security.RefreshTokenStore;
import com.glassvue.global.security.TokenRevocationStore;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 회원 조작(B-11 후속) — 정지/해제 · 역할변경. 권한은 <b>엄격 분리</b>(2026-07-28):
 *
 * <ul>
 *   <li>자기 자신은 조작 불가(락아웃 방지, {@code CANNOT_MODIFY_SELF}).
 *   <li>SUPER_ADMIN 계정은 <b>아무도</b> 정지·변경 못 함({@code CANNOT_MODIFY_SUPER_ADMIN}).
 *   <li>관리자(ADMIN) 계정의 정지, 그리고 <b>모든 역할 변경</b>은 SUPER_ADMIN 만({@code SUPER_ADMIN_ONLY}).
 *       일반 ADMIN 은 일반 회원(USER)만 정지/해제할 수 있다.
 *   <li>역할 변경으로 SUPER_ADMIN 을 <b>부여할 수 없다</b>({@code CANNOT_GRANT_SUPER_ADMIN}) — 최상위는
 *       배포 후 별도 데이터 작업으로만 정한다.
 * </ul>
 *
 * <p>행위자 정보는 JWT 클레임(=AuthUser: id·role·nickname)에서 온다. 성공한 조작은 {@link AdminActionEvent}
 * 로 발행해 감사 이력에 남긴다 — 리스너가 <b>같은 트랜잭션</b>에서 저장하므로, 감사 기록이 실패하면 조작도
 * 함께 롤백된다(감사 무결성). audit 도메인을 직접 부르지 않고 이벤트로만 통신한다(도메인 경계).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MemberAdminCommandService {

    private final MemberRepository memberRepository;
    private final RefreshTokenStore refreshTokenStore;
    private final TokenRevocationStore tokenRevocationStore;
    private final ApplicationEventPublisher eventPublisher;

    public AdminMemberResponse suspend(AuthUser actor, UUID targetId) {
        Member member = authorize(actor, targetId, false);
        member.suspend();
        refreshTokenStore.delete(targetId); // 갱신 차단
        tokenRevocationStore.revokeAll(targetId); // 이미 나가 있는 access 토큰도 즉시 무효
        log.info("Member suspended: target={} by admin={}", targetId, actor.id());
        publish(AuditAction.MEMBER_SUSPEND, actor, member, null);
        return AdminMemberResponse.from(member);
    }

    public AdminMemberResponse unsuspend(AuthUser actor, UUID targetId) {
        Member member = authorize(actor, targetId, false);
        member.unsuspend();
        log.info("Member unsuspended: target={} by admin={}", targetId, actor.id());
        publish(AuditAction.MEMBER_UNSUSPEND, actor, member, null);
        return AdminMemberResponse.from(member);
    }

    public AdminMemberResponse changeRole(AuthUser actor, UUID targetId, Role newRole) {
        if (newRole == Role.SUPER_ADMIN) {
            throw new BusinessException(ErrorCode.CANNOT_GRANT_SUPER_ADMIN);
        }
        Member member = authorize(actor, targetId, true); // 역할변경은 항상 SUPER_ADMIN 전용
        Role oldRole = member.getRole();
        member.changeRole(newRole);
        // ⚠ 역할은 JWT 클레임에 박혀 있다 — 컷오프가 없으면 강등된 관리자가 access 만료까지(최대 30분)
        // 관리자 권한을 계속 쓴다. refresh 는 지우지 않는다: 그걸로 재발급하면 **새 역할**이 박히므로
        // 오히려 정상 경로다(정지와 다른 점 — 정지는 재발급 자체를 막아야 한다).
        tokenRevocationStore.revokeAll(targetId);
        log.info("Member role changed to {}: target={} by admin={}", newRole, targetId, actor.id());
        publish(AuditAction.MEMBER_ROLE_CHANGE, actor, member, oldRole + " → " + newRole);
        return AdminMemberResponse.from(member);
    }

    private void publish(AuditAction action, AuthUser actor, Member target, String detail) {
        eventPublisher.publishEvent(new AdminActionEvent(
                action, actor.id(), actor.nickname(), target.getId(), target.getLoginId(), detail));
    }

    /**
     * 대상 회원을 찾고 권한을 검증한다.
     *
     * @param superOnly 대상이 USER 여도 SUPER_ADMIN 만 허용(역할변경용). false 면 대상이 ADMIN 일 때만 SUPER 요구.
     */
    private Member authorize(AuthUser actor, UUID targetId, boolean superOnly) {
        if (actor.id().equals(targetId)) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_SELF);
        }
        Member target = memberRepository.findById(targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (target.getRole() == Role.SUPER_ADMIN) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_SUPER_ADMIN);
        }
        boolean needsSuper = superOnly || target.getRole() == Role.ADMIN;
        if (needsSuper && actor.role() != Role.SUPER_ADMIN) {
            throw new BusinessException(ErrorCode.SUPER_ADMIN_ONLY);
        }
        return target;
    }
}
