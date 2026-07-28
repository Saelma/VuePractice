package com.glassvue.domain.member.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.security.RefreshTokenStore;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberAdminCommandServiceTest {

    @Mock MemberRepository memberRepository;
    @Mock RefreshTokenStore refreshTokenStore;
    @InjectMocks MemberAdminCommandService service;

    private final UUID adminId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();

    private Member user() {
        return Member.builder().loginId("kim").password("H").nickname("김철수").role(Role.USER).build();
    }

    private static void assertError(Runnable r, ErrorCode expected) {
        assertThatThrownBy(r::run).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(expected);
    }

    @Test
    @DisplayName("정지: 상태 true + refresh 삭제 + 응답 반영")
    void suspend_ok() {
        Member m = user();
        when(memberRepository.findById(targetId)).thenReturn(Optional.of(m));
        var res = service.suspend(adminId, targetId);
        assertThat(m.isSuspended()).isTrue();
        assertThat(res.suspended()).isTrue();
        verify(refreshTokenStore).delete(targetId); // 기존 세션 무효화
    }

    @Test
    @DisplayName("정지 해제: 상태 false")
    void unsuspend_ok() {
        Member m = user();
        m.suspend();
        when(memberRepository.findById(targetId)).thenReturn(Optional.of(m));
        var res = service.unsuspend(adminId, targetId);
        assertThat(m.isSuspended()).isFalse();
        assertThat(res.suspended()).isFalse();
    }

    @Test
    @DisplayName("역할 변경: USER → ADMIN")
    void changeRole_ok() {
        Member m = user();
        when(memberRepository.findById(targetId)).thenReturn(Optional.of(m));
        var res = service.changeRole(adminId, targetId, Role.ADMIN);
        assertThat(m.getRole()).isEqualTo(Role.ADMIN);
        assertThat(res.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("자기 자신은 정지 불가 → CANNOT_MODIFY_SELF, 조회조차 안 함")
    void suspend_self_rejected() {
        assertError(() -> service.suspend(adminId, adminId), ErrorCode.CANNOT_MODIFY_SELF);
        verify(memberRepository, never()).findById(any());
        verify(refreshTokenStore, never()).delete(any());
    }

    @Test
    @DisplayName("자기 자신은 역할 변경 불가 → CANNOT_MODIFY_SELF")
    void changeRole_self_rejected() {
        assertError(() -> service.changeRole(adminId, adminId, Role.USER), ErrorCode.CANNOT_MODIFY_SELF);
        verify(memberRepository, never()).findById(any());
    }

    @Test
    @DisplayName("없는 회원 정지 → MEMBER_NOT_FOUND")
    void suspend_notFound() {
        when(memberRepository.findById(targetId)).thenReturn(Optional.empty());
        assertError(() -> service.suspend(adminId, targetId), ErrorCode.MEMBER_NOT_FOUND);
    }
}
