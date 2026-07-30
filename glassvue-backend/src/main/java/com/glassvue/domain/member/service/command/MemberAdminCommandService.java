package com.glassvue.domain.member.service.command;

import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.domain.audit.event.AdminActionEvent;
import com.glassvue.domain.member.dto.AdminMemberResponse;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.domain.member.service.MemberService;
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
 * 관리자 회원 조작(B-11 후속) — 정지/해제 · 역할변경 · <b>강제 삭제</b>(B-24). 권한은 <b>엄격 분리</b>(2026-07-28):
 *
 * <ul>
 *   <li>자기 자신은 조작 불가(락아웃 방지, {@code CANNOT_MODIFY_SELF}).
 *   <li>SUPER_ADMIN 계정은 <b>아무도</b> 정지·변경 못 함({@code CANNOT_MODIFY_SUPER_ADMIN}).
 *   <li>관리자(ADMIN) 계정의 정지, 그리고 <b>모든 역할 변경</b>은 SUPER_ADMIN 만({@code SUPER_ADMIN_ONLY}).
 *       일반 ADMIN 은 일반 회원(USER)만 정지/해제할 수 있다.
 *   <li>역할 변경으로 SUPER_ADMIN 을 <b>부여할 수 없다</b>({@code CANNOT_GRANT_SUPER_ADMIN}) — 최상위는
 *       배포 후 별도 데이터 작업으로만 정한다.
 *   <li><b>강제 삭제는 SUPER_ADMIN 만</b>(2026-07-30) — 되돌릴 수 없어 역할변경과 같은 급으로 봤다.
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
    private final MemberService memberService;
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

    /**
     * 회원 강제 삭제(B-24, 2026-07-30) — <b>SUPER_ADMIN 전용</b>.
     *
     * <p>왜 필요했나: 탈퇴는 {@code DELETE /api/members/me} <b>본인 전용</b>이라, 비밀번호를 모르는 계정은
     * <b>정상 경로로 지울 방법이 아예 없었다.</b> 2026-07-30 잔재 점검에서 7/28 검증용 ADMIN 계정을
     * 발견했을 때 남은 선택이 DB 직접 DELETE 뿐이었다 — 그건 토큰 무효화·연관 정리를 건너뛴다.
     *
     * <p>⚠ <b>정지보다 무거운 조작이라 권한도 한 칸 좁다</b>({@code superOnly=true}): 대상이 일반 USER 여도
     * SUPER_ADMIN 만 지울 수 있다. 역할변경과 같은 급으로 본 것이다 — 되돌릴 수 없기 때문이다.
     * {@code authorize} 가 <b>자기 자신</b>(락아웃)과 <b>SUPER_ADMIN 대상</b>도 함께 막는다.
     *
     * <p>⚠ 스냅샷을 <b>지우기 전에</b> 읽는다 — 감사 이력의 {@code target_login} 은 대상이 사라진 뒤에도
     * 읽혀야 하는 값이다(그게 audit 이 FK 를 두지 않는 이유다). 삭제 후에 읽으면 값이 없다.
     *
     * <p>실제 삭제는 {@code MemberService.purge} 에 위임한다 — <b>본인 탈퇴와 같은 경로</b>여야
     * "관리자로 지운 회원만 데이터가 남는" 어긋남이 안 생긴다(F-1).
     */
    public void delete(AuthUser actor, UUID targetId) {
        Member member = authorize(actor, targetId, true);
        // ⚠ 감사 발행이 **삭제보다 먼저**다 — publish 가 member.getLoginId() 를 스냅샷으로 읽는데,
        // 지운 뒤에는 그 값을 읽을 수 없다(대상이 사라진 뒤에도 읽히는 값이어야 한다).
        publish(AuditAction.MEMBER_DELETE, actor, member, null);
        memberService.purge(member);
        log.info("Member deleted by admin: target={} by admin={}", targetId, actor.id());
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
