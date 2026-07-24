package com.glassvue.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.glassvue.domain.notification.dto.NotificationSettingResponse;
import com.glassvue.domain.notification.entity.NotificationPref;
import com.glassvue.domain.notification.entity.NotificationType;
import com.glassvue.domain.notification.repository.NotificationPrefRepository;
import com.glassvue.domain.notification.repository.NotificationRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock NotificationPrefRepository prefRepository;
    @InjectMocks NotificationQueryService service;

    private final UUID member = UUID.randomUUID();

    @Test
    @DisplayName("설정 조회는 모든 타입을 내려주고, 행이 없는 타입은 켜짐(기본 on)으로 채운다")
    void settingsFillDefaults() {
        // STOCK 만 꺼둔 상태 저장 — ORDER 는 행이 없다
        when(prefRepository.findByMemberId(member))
                .thenReturn(List.of(NotificationPref.of(member, NotificationType.STOCK, false)));

        List<NotificationSettingResponse> settings = service.settings(member);

        assertThat(settings).hasSize(NotificationType.values().length);
        assertThat(settings).anySatisfy(s -> {
            assertThat(s.type()).isEqualTo(NotificationType.ORDER);
            assertThat(s.enabled()).isTrue(); // 행 없음 → 기본 on
        });
        assertThat(settings).anySatisfy(s -> {
            assertThat(s.type()).isEqualTo(NotificationType.STOCK);
            assertThat(s.enabled()).isFalse(); // 꺼둔 것 반영
        });
    }
}
