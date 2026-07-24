package com.glassvue.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    @InjectMocks NotificationCommandService service;

    private final UUID member = UUID.randomUUID();

    @Test
    @DisplayName("설정이 켜져 있으면(기본 on) 저장하고 SSE 로 민다")
    void createsAndPushesWhenEnabled() {
        when(prefRepository.findByMemberIdAndType(member, NotificationType.ORDER)).thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(member, NotificationType.ORDER, "제목", "내용", "/orders/x");

        verify(notificationRepository).save(any(Notification.class));
        verify(stream).push(eq(member), any(NotificationResponse.class));
    }

    @Test
    @DisplayName("그 타입을 껐으면 만들지도 밀지도 않는다(opt-out)")
    void skipsWhenDisabled() {
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
