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
 * 관리자 회원 조작(B-11 후속) — 정지/해제 · 역할변경.
 *
 * <p><b>자기 자신은 조작 못 한다</b>(사용자 결정) — 관리자가 자기 계정을 강등·정지하면 그 자리에서
 * 락아웃된다. 다른 관리자에 대한 조작은 허용하되(문제 관리자 강등 여지) 모두 로그로 남긴다.
 * 감사 테이블은 아직 없어 SLF4J 로만 남긴다(별도 감사 로그는 BACKLOG 후속).
 *
 * <p>정지 시 그 회원의 refresh 토큰을 지운다 — 이미 로그인된 세션이 access 만료(≤30분) 뒤 갱신에
 * 실패해 끊긴다. 그 30분 창에서도 로그인·주문은 각 도메인 가드(Auth·Order)가 막는다(전면 차단).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MemberAdminCommandService {

    private final MemberRepository memberRepository;
    private final RefreshTokenStore refreshTokenStore;

    public AdminMemberResponse suspend(UUID actingAdminId, UUID targetId) {
        Member member = target(actingAdminId, targetId);
        member.suspend();
        refreshTokenStore.delete(targetId); // 기존 세션 무효화(갱신 차단)
        log.info("Member suspended: target={} by admin={}", targetId, actingAdminId);
        return AdminMemberResponse.from(member);
    }

    public AdminMemberResponse unsuspend(UUID actingAdminId, UUID targetId) {
        Member member = target(actingAdminId, targetId);
        member.unsuspend();
        log.info("Member unsuspended: target={} by admin={}", targetId, actingAdminId);
        return AdminMemberResponse.from(member);
    }

    public AdminMemberResponse changeRole(UUID actingAdminId, UUID targetId, Role role) {
        Member member = target(actingAdminId, targetId);
        member.changeRole(role);
        log.info("Member role changed to {}: target={} by admin={}", role, targetId, actingAdminId);
        return AdminMemberResponse.from(member);
    }

    /** 대상 회원을 찾되, 자기 자신이면 거부한다(락아웃 방지). */
    private Member target(UUID actingAdminId, UUID targetId) {
        if (actingAdminId.equals(targetId)) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_SELF);
        }
        return memberRepository.findById(targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
