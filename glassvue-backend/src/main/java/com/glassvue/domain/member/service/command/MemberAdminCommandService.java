package com.glassvue.domain.member.service.command;

import com.glassvue.domain.member.dto.AdminMemberResponse;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.security.RefreshTokenStore;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * <p>{@code actingRole} 은 JWT 클레임(=AuthUser)에서 온다. 감사는 SLF4J 로만(감사 테이블은 후속).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MemberAdminCommandService {

    private final MemberRepository memberRepository;
    private final RefreshTokenStore refreshTokenStore;

    public AdminMemberResponse suspend(UUID actingId, Role actingRole, UUID targetId) {
        Member member = authorize(actingId, actingRole, targetId, false);
        member.suspend();
        refreshTokenStore.delete(targetId); // 기존 세션 무효화(갱신 차단)
        log.info("Member suspended: target={} by admin={}", targetId, actingId);
        return AdminMemberResponse.from(member);
    }

    public AdminMemberResponse unsuspend(UUID actingId, Role actingRole, UUID targetId) {
        Member member = authorize(actingId, actingRole, targetId, false);
        member.unsuspend();
        log.info("Member unsuspended: target={} by admin={}", targetId, actingId);
        return AdminMemberResponse.from(member);
    }

    public AdminMemberResponse changeRole(UUID actingId, Role actingRole, UUID targetId, Role newRole) {
        if (newRole == Role.SUPER_ADMIN) {
            throw new BusinessException(ErrorCode.CANNOT_GRANT_SUPER_ADMIN);
        }
        Member member = authorize(actingId, actingRole, targetId, true); // 역할변경은 항상 SUPER_ADMIN 전용
        member.changeRole(newRole);
        log.info("Member role changed to {}: target={} by admin={}", newRole, targetId, actingId);
        return AdminMemberResponse.from(member);
    }

    /**
     * 대상 회원을 찾고 권한을 검증한다.
     *
     * @param superOnly 대상이 USER 여도 SUPER_ADMIN 만 허용(역할변경용). false 면 대상이 ADMIN 일 때만 SUPER 요구.
     */
    private Member authorize(UUID actingId, Role actingRole, UUID targetId, boolean superOnly) {
        if (actingId.equals(targetId)) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_SELF);
        }
        Member target = memberRepository.findById(targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (target.getRole() == Role.SUPER_ADMIN) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_SUPER_ADMIN);
        }
        boolean needsSuper = superOnly || target.getRole() == Role.ADMIN;
        if (needsSuper && actingRole != Role.SUPER_ADMIN) {
            throw new BusinessException(ErrorCode.SUPER_ADMIN_ONLY);
        }
        return target;
    }
}
