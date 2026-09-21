package com.glassvue.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassvue.domain.member.service.MemberService;
import com.glassvue.domain.notification.dto.NotificationResponse;
import com.glassvue.domain.notification.entity.Notification;
import com.glassvue.domain.notification.entity.NotificationPref;
import com.glassvue.domain.notification.entity.NotificationType;
import com.glassvue.domain.notification.repository.NotificationPrefRepository;
import com.glassvue.domain.notification.repository.NotificationRepository;
import com.glassvue.domain.notification.sse.NotificationStream;
import com.glassvue.global.exception.BusinessException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationCommandServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock NotificationPrefRepository prefRepository;
    @Mock NotificationStream stream;
    @Mock MemberService memberService;
    @InjectMocks NotificationCommandService service;

    private final UUID member = UUID.randomUUID();

    @Test
    @DisplayName("설정이 켜져 있으면(기본 on) 저장하고 SSE 로 민다")
    void createsAndPushesWhenEnabled() {
        when(memberService.exists(member)).thenReturn(true);
        when(prefRepository.findByMemberIdAndType(member, NotificationType.ORDER)).thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(member, NotificationType.ORDER, "제목", "내용", "/orders/x");

        verify(notificationRepository).save(any(Notification.class));
        verify(stream).push(eq(member), any(NotificationResponse.class));
    }

    // ── 🔴 받을 사람이 없으면 만들지 않는다 (2026-09-21, BACKLOG §F-9) ──────────────
    //
    // 탈퇴는 하드 삭제인데 주문·리뷰는 남는다(F-1). 그래서 **탈퇴 뒤에** 관리자가 그 주문을 취소하면
    // 아무도 영원히 못 읽는 알림이 생겼다(2026-09-21 실측 — 불변식 ⑬ 이 그날 처음 1 이 됐다).
    // ⚠ 기존 방어 셋은 전부 «탈퇴 시점» 을 본다 — 그 뒤에 생기는 것은 아무도 안 막았다.

    @Test
    @DisplayName("🔴 받을 회원이 **없으면** 만들지도 밀지도 않는다 — 아무도 못 읽는 알림이 된다 (§F-9)")
    void skipsWhenMemberGone() {
        when(memberService.exists(member)).thenReturn(false);

        boolean created = service.create(member, NotificationType.ORDER, "제목", "내용", "/orders/x");

        assertThat(created).isFalse();
        verify(notificationRepository, never()).save(any());
        verify(stream, never()).push(any(), any());
    }

    @Test
    @DisplayName("⚠ 없는 회원의 **설정을 묻지도 않는다** — 뜻이 없는 조회다(순서가 규약이다)")
    void doesNotAskPrefWhenMemberGone() {
        when(memberService.exists(member)).thenReturn(false);

        service.create(member, NotificationType.ORDER, "제목", "내용", "/orders/x");

        // 🔴 이 단언이 빨개지면 «회원 확인» 이 설정 조회 **뒤로** 밀린 것이다.
        verify(prefRepository, never()).findByMemberIdAndType(any(), any());
    }

    @Test
    @DisplayName("그 타입을 껐으면 만들지도 밀지도 않는다(opt-out)")
    void skipsWhenDisabled() {
        when(memberService.exists(member)).thenReturn(true);
        when(prefRepository.findByMemberIdAndType(member, NotificationType.ORDER))
                .thenReturn(Optional.of(NotificationPref.of(member, NotificationType.ORDER, false)));

        service.create(member, NotificationType.ORDER, "제목", "내용", "/orders/x");

        verify(notificationRepository, never()).save(any());
        verify(stream, never()).push(any(), any());
    }

    @Test
    @DisplayName("읽음 처리는 본인 알림만 — 없으면 NOTIFICATION_NOT_FOUND")
    void markReadOwnership() {
        UUID id = UUID.randomUUID();
        when(notificationRepository.findByIdAndMemberId(id, member)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(id, member))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("설정 변경: UPDATE가 0행이면(없음) 새로 저장한다(upsert)")
    void changeSettingInsertsWhenAbsent() {
        when(prefRepository.updateEnabled(member, NotificationType.STOCK, false)).thenReturn(0);

        service.changeSetting(member, NotificationType.STOCK, false);

        verify(prefRepository).save(any(NotificationPref.class));
    }

    @Test
    @DisplayName("설정 변경: 이미 있으면 UPDATE만 하고 INSERT 하지 않는다(유니크 경합 회피)")
    void changeSettingUpdatesWhenPresent() {
        when(prefRepository.updateEnabled(member, NotificationType.ORDER, false)).thenReturn(1);

        service.changeSetting(member, NotificationType.ORDER, false);

        verify(prefRepository, never()).save(any());
    }
}
