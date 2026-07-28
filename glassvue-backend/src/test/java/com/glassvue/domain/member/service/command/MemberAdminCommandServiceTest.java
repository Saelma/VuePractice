package com.glassvue.domain.member.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.domain.audit.event.AdminActionEvent;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.security.AuthUser;
import com.glassvue.global.security.RefreshTokenStore;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class MemberAdminCommandServiceTest {

    @Mock MemberRepository memberRepository;
    @Mock RefreshTokenStore refreshTokenStore;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks MemberAdminCommandService service;

    private final UUID adminId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();

    private AuthUser actor(Role role) {
        return new AuthUser(adminId, role, "관리자");
    }

    private Member member(Role role) {
        return Member.builder().loginId("t").password("H").nickname("대상").role(role).build();
    }

    private static void assertError(Runnable r, ErrorCode expected) {
        assertThatThrownBy(r::run).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(expected);
    }

    // ---------- 일반 ADMIN 이 일반 회원(USER)에 하는 것: 허용 ----------

    @Test
    @DisplayName("일반관리자 정지(USER 대상): 상태 true + refresh 삭제")
    void suspend_userByAdmin() {
        Member m = member(Role.USER);
        when(memberRepository.findById(targetId)).thenReturn(Optional.of(m));
        var res = service.suspend(actor(Role.ADMIN), targetId);
        assertThat(m.isSuspended()).isTrue();
        assertThat(res.suspended()).isTrue();
        verify(refreshTokenStore).delete(targetId);
    }

    @Test
    @DisplayName("일반관리자 정지 해제(USER 대상)")
    void unsuspend_userByAdmin() {
        Member m = member(Role.USER);
        m.suspend();
        when(memberRepository.findById(targetId)).thenReturn(Optional.of(m));
        assertThat(service.unsuspend(actor(Role.ADMIN), targetId).suspended()).isFalse();
    }

    // ---------- 역할 변경: SUPER_ADMIN 전용 ----------

    @Test
    @DisplayName("역할 변경은 SUPER_ADMIN 만: USER → ADMIN")
    void changeRole_bySuper() {
        Member m = member(Role.USER);
        when(memberRepository.findById(targetId)).thenReturn(Optional.of(m));
        assertThat(service.changeRole(actor(Role.SUPER_ADMIN), targetId, Role.ADMIN).role()).isEqualTo(Role.ADMIN);
        assertThat(m.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("일반관리자는 역할 변경 불가 → SUPER_ADMIN_ONLY")
    void changeRole_byAdmin_forbidden() {
        Member m = member(Role.USER);
        when(memberRepository.findById(targetId)).thenReturn(Optional.of(m));
        assertError(() -> service.changeRole(actor(Role.ADMIN), targetId, Role.ADMIN), ErrorCode.SUPER_ADMIN_ONLY);
    }

    @Test
    @DisplayName("SUPER_ADMIN 부여는 이 API로 불가 → CANNOT_GRANT_SUPER_ADMIN(조회조차 안 함)")
    void changeRole_toSuper_forbidden() {
        assertError(() -> service.changeRole(actor(Role.SUPER_ADMIN), targetId, Role.SUPER_ADMIN),
                ErrorCode.CANNOT_GRANT_SUPER_ADMIN);
        verify(memberRepository, never()).findById(any());
    }

    // ---------- 관리자 정지: SUPER_ADMIN 전용 ----------

    @Test
    @DisplayName("일반관리자가 ADMIN 정지 → SUPER_ADMIN_ONLY")
    void suspend_adminByAdmin_forbidden() {
        when(memberRepository.findById(targetId)).thenReturn(Optional.of(member(Role.ADMIN)));
        assertError(() -> service.suspend(actor(Role.ADMIN), targetId), ErrorCode.SUPER_ADMIN_ONLY);
    }

    @Test
    @DisplayName("SUPER_ADMIN 이 ADMIN 정지 → 허용")
    void suspend_adminBySuper() {
        Member m = member(Role.ADMIN);
        when(memberRepository.findById(targetId)).thenReturn(Optional.of(m));
        assertThat(service.suspend(actor(Role.SUPER_ADMIN), targetId).suspended()).isTrue();
    }

    // ---------- SUPER_ADMIN 대상: 아무도 못 건드림 ----------

    @Test
    @DisplayName("SUPER_ADMIN 대상은 SUPER_ADMIN 이어도 정지 불가 → CANNOT_MODIFY_SUPER_ADMIN")
    void suspend_superTarget_forbidden() {
        when(memberRepository.findById(targetId)).thenReturn(Optional.of(member(Role.SUPER_ADMIN)));
        assertError(() -> service.suspend(actor(Role.SUPER_ADMIN), targetId), ErrorCode.CANNOT_MODIFY_SUPER_ADMIN);
    }

    // ---------- 감사 이벤트 발행 ----------

    @Test
    @DisplayName("정지 성공 시 감사 이벤트 발행: 종류·행위자·대상 스냅샷")
    void suspend_publishesAuditEvent() {
        Member m = member(Role.USER);
        when(memberRepository.findById(targetId)).thenReturn(Optional.of(m));
        service.suspend(actor(Role.ADMIN), targetId);

        ArgumentCaptor<AdminActionEvent> captor = ArgumentCaptor.forClass(AdminActionEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        AdminActionEvent e = captor.getValue();
        assertThat(e.action()).isEqualTo(AuditAction.MEMBER_SUSPEND);
        assertThat(e.actorId()).isEqualTo(adminId);
        assertThat(e.actorName()).isEqualTo("관리자");
        assertThat(e.targetLogin()).isEqualTo("t"); // 대상 loginId 스냅샷
        assertThat(e.detail()).isNull();
    }

    @Test
    @DisplayName("역할 변경 시 감사 이벤트 detail 에 전/후 기록")
    void changeRole_publishesAuditEventWithDetail() {
        Member m = member(Role.USER);
        when(memberRepository.findById(targetId)).thenReturn(Optional.of(m));
        service.changeRole(actor(Role.SUPER_ADMIN), targetId, Role.ADMIN);

        ArgumentCaptor<AdminActionEvent> captor = ArgumentCaptor.forClass(AdminActionEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        AdminActionEvent e = captor.getValue();
        assertThat(e.action()).isEqualTo(AuditAction.MEMBER_ROLE_CHANGE);
        assertThat(e.detail()).isEqualTo("USER → ADMIN");
    }

    @Test
    @DisplayName("권한 거부 시 감사 이벤트는 발행되지 않는다")
    void forbidden_doesNotPublish() {
        when(memberRepository.findById(targetId)).thenReturn(Optional.of(member(Role.ADMIN)));
        assertError(() -> service.suspend(actor(Role.ADMIN), targetId), ErrorCode.SUPER_ADMIN_ONLY);
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ---------- 자기 자신 / 없는 회원 ----------

    @Test
    @DisplayName("자기 자신 정지 → CANNOT_MODIFY_SELF, 조회조차 안 함")
    void suspend_self_rejected() {
        assertError(() -> service.suspend(actor(Role.SUPER_ADMIN), adminId), ErrorCode.CANNOT_MODIFY_SELF);
        verify(memberRepository, never()).findById(any());
        verify(refreshTokenStore, never()).delete(any());
    }

    @Test
    @DisplayName("없는 회원 정지 → MEMBER_NOT_FOUND")
    void suspend_notFound() {
        when(memberRepository.findById(targetId)).thenReturn(Optional.empty());
        assertError(() -> service.suspend(actor(Role.ADMIN), targetId), ErrorCode.MEMBER_NOT_FOUND);
    }
}
