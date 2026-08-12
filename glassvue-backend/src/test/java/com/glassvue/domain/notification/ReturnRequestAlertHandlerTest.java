package com.glassvue.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassvue.domain.member.service.MemberService;
import com.glassvue.domain.notification.entity.NotificationType;
import com.glassvue.domain.notification.service.NotificationCommandService;
import com.glassvue.domain.order.event.OrderReturnRequestedEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 반품 요청 알림 핸들러 (2026-08-12, 08-11 이월).
 *
 * <p>핸들러를 <b>직접 부른다</b> — {@code @Async} + {@code AFTER_COMMIT} 뒤에 있어 통합 테스트에서는
 * 커밋이 없어 안 돌아간다({@link OrderNotificationHandlerTest} 가 같은 이유로 그렇게 한다).
 *
 * <p>여기서 못 박는 것은 <b>"누구에게 · 어느 종류로 · 어디로"</b> 다. 🔴 셋 다 틀려도
 * <b>알림은 정상적으로 생성되고 화면에도 뜬다</b> — 그래서 통합·컴파일로는 안 드러난다.
 */
@ExtendWith(MockitoExtension.class)
class ReturnRequestAlertHandlerTest {

    @Mock MemberService memberService;
    @Mock NotificationCommandService notificationService;
    @InjectMocks ReturnRequestAlertHandler handler;

    private final UUID orderId = UUID.randomUUID();
    private final UUID buyerId = UUID.randomUUID();
    private final UUID adminA = UUID.randomUUID();
    private final UUID adminB = UUID.randomUUID();

    private OrderReturnRequestedEvent event(String reason) {
        return new OrderReturnRequestedEvent(orderId, buyerId, "20260812-0001", "ZZ구매자", reason);
    }

    @Test
    @DisplayName("🔴 관리자 **전원**에게 간다 — 한 명에게만 가면 그 사람이 쉬는 날 아무도 모른다")
    void notifiesEveryAdmin() {
        when(memberService.adminIds()).thenReturn(List.of(adminA, adminB));

        handler.handle(event("변심"));

        ArgumentCaptor<UUID> to = ArgumentCaptor.forClass(UUID.class);
        verify(notificationService, times(2))
                .create(to.capture(), any(), anyString(), anyString(), anyString());
        assertThat(to.getAllValues()).containsExactlyInAnyOrder(adminA, adminB);
    }

    @Test
    @DisplayName("🔴 종류는 **RETURN_REQUEST** 다 — `ORDER` 로 보내면 관리자가 자기 주문 알림을 끌 때 함께 꺼진다")
    void usesReturnRequestType() {
        when(memberService.adminIds()).thenReturn(List.of(adminA));

        handler.handle(event("변심"));

        verify(notificationService).create(eq(adminA), eq(NotificationType.RETURN_REQUEST),
                anyString(), anyString(), anyString());
        verify(notificationService, never()).create(any(), eq(NotificationType.ORDER),
                anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("⚠ 링크는 **그 주문 상세**다 — 승인·거절을 하는 자리가 거기다(가리키는 곳이 있어야 안내다)")
    void linksToTheOrder() {
        when(memberService.adminIds()).thenReturn(List.of(adminA));

        handler.handle(event("변심"));

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(notificationService).create(any(), any(), anyString(), anyString(), link.capture());
        assertThat(link.getValue()).isEqualTo("/orders/" + orderId);
    }

    @Test
    @DisplayName("제목에 **주문번호**가 들어간다 — 관리자는 여러 건 중에서 골라야 한다")
    void titleCarriesOrderNo() {
        when(memberService.adminIds()).thenReturn(List.of(adminA));

        handler.handle(event("변심"));

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        verify(notificationService).create(any(), any(), title.capture(), anyString(), anyString());
        assertThat(title.getValue()).contains("20260812-0001");
    }

    @Test
    @DisplayName("사유가 있으면 문구에 싣는다 — 관리자가 열어 보기 전에 판단할 수 있다")
    void messageCarriesReason() {
        when(memberService.adminIds()).thenReturn(List.of(adminA));

        handler.handle(event("사이즈가 안 맞아요"));

        assertThat(message()).contains("ZZ구매자").contains("사유: 사이즈가 안 맞아요");
    }

    @Test
    @DisplayName("⚠ 사유는 **선택값**이다 — 없을 때 «사유: » 로 끝나면 값이 지워진 것처럼 읽힌다")
    void messageWithoutReason() {
        when(memberService.adminIds()).thenReturn(List.of(adminA));

        handler.handle(event(null));

        assertThat(message()).contains("(사유 미입력)").doesNotContain("사유: ");
    }

    @Test
    @DisplayName("⚠ 공백만 들어와도 «없음» 으로 본다(빈 문자열과 공백을 갈라 다루지 않는다)")
    void messageWithBlankReason() {
        when(memberService.adminIds()).thenReturn(List.of(adminA));

        handler.handle(event("   "));

        assertThat(message()).contains("(사유 미입력)");
    }

    @Test
    @DisplayName("⚠ 관리자가 **없으면** 아무 일도 안 한다 — 터지지 않는다")
    void noAdmins_doesNothing() {
        when(memberService.adminIds()).thenReturn(List.of());

        handler.handle(event("변심"));

        verify(notificationService, never()).create(any(), any(), anyString(), anyString(), anyString());
    }

    private String message() {
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notificationService).create(any(), any(), anyString(), message.capture(), anyString());
        return message.getValue();
    }
}
