package com.glassvue.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.glassvue.domain.notification.entity.NotificationType;
import com.glassvue.domain.notification.service.NotificationCommandService;
import com.glassvue.domain.order.event.OrderCancelledEvent;
import com.glassvue.domain.order.event.OrderDeliveredEvent;
import com.glassvue.domain.order.event.OrderPlacedEvent;
import com.glassvue.domain.order.event.OrderReturnRejectedEvent;
import com.glassvue.domain.order.event.OrderReturnedEvent;
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
 * 주문 알림 핸들러 (H-6, 2026-07-31).
 *
 * <p><b>왜 이 파일이 이제야 생겼나</b>: H-5(JaCoCo)를 붙이고 나서야 이 클래스가 <b>4% · 분기 0%</b>,
 * 즉 <b>한 번도 실행된 적이 없다</b>는 게 드러났다. 같은 패키지의 {@link OrderEventListener} 는
 * 100% 였다 — <b>어댑터는 덮였고 진짜 주체는 비어 있었다.</b>
 *
 * <p>원인은 구조적이다. 핸들러는 {@code @Async} + {@code AFTER_COMMIT} 뒤에 있어서
 * {@code @Transactional} 통합 테스트에서는 <b>커밋이 없어 리스너가 뜨지 않는다.</b>
 * 통합을 아무리 늘려도 이 층은 안 돌아간다 → <b>핸들러는 직접 부른다</b>
 * ({@link InquiryNotificationHandlerTest}·{@code RestockNotificationHandlerTest} 와 같은 방식).
 *
 * <p>여기서 못박는 것은 <b>"누구에게 · 무엇을 · 어디로"</b> 다. 셋 다 틀려도 알림은 정상적으로
 * 생성되므로 컴파일·통합으로는 드러나지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class OrderNotificationHandlerTest {

    @Mock NotificationCommandService notificationService;
    @InjectMocks OrderNotificationHandler handler;

    private final UUID orderId = UUID.randomUUID();
    private final UUID buyerId = UUID.randomUUID();

    /** 생성된 알림 한 건. 인자 다섯 개를 매번 캡처하는 잡음을 여기로 모은다. */
    private record Notified(UUID memberId, NotificationType type, String title, String message, String link) {}

    private Notified captured() {
        ArgumentCaptor<UUID> member = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<NotificationType> type = ArgumentCaptor.forClass(NotificationType.class);
        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(notificationService).create(
                member.capture(), type.capture(), title.capture(), message.capture(), link.capture());
        return new Notified(member.getValue(), type.getValue(),
                title.getValue(), message.getValue(), link.getValue());
    }

    // ── 주문 접수 ─────────────────────────────────────────────

    @Test
    @DisplayName("주문 접수 → **구매자**에게 ORDER 알림, 링크는 그 주문 상세")
    void placedNotifiesBuyer() {
        handler.handle(new OrderPlacedEvent(orderId, buyerId, 30_000L, 2, List.of()));

        Notified n = captured();
        assertThat(n.memberId()).isEqualTo(buyerId);   // 주문 id 가 아니다(둘 다 UUID 라 바꿔 써도 컴파일된다)
        assertThat(n.type()).isEqualTo(NotificationType.ORDER);
        assertThat(n.message()).contains("2건");        // 몇 건 샀는지가 없으면 알림이 무의미해진다
        assertThat(n.link()).isEqualTo("/orders/" + orderId);
    }

    // ── 배송완료 (⚠ 적립 문구 분기) ────────────────────────────

    @Test
    @DisplayName("배송완료 + 적립 → 적립액을 문구에 담는다")
    void deliveredWithPoint() {
        handler.handle(new OrderDeliveredEvent(orderId, buyerId, 50_000L, 500L));

        Notified n = captured();
        assertThat(n.memberId()).isEqualTo(buyerId);
        assertThat(n.message()).contains("500P 적립");
        assertThat(n.link()).isEqualTo("/orders/" + orderId);
    }

    @Test
    @DisplayName("⚠ 적립이 0이면 적립 문구를 **넣지 않는다** — \"0P 적립되었습니다\"는 안내가 아니라 혼란이다")
    void deliveredWithoutPoint() {
        handler.handle(new OrderDeliveredEvent(orderId, buyerId, 0L, 0L));

        Notified n = captured();
        assertThat(n.message()).isEqualTo("배송이 완료되었어요.");
        assertThat(n.message()).doesNotContain("적립");
    }

    // ── 주문 취소 ─────────────────────────────────────────────

    @Test
    @DisplayName("주문 취소 → 구매자에게 취소 금액과 함께")
    void cancelledNotifiesBuyer() {
        handler.handle(new OrderCancelledEvent(orderId, buyerId, 30_000L, 2, List.of()));

        Notified n = captured();
        assertThat(n.memberId()).isEqualTo(buyerId);
        assertThat(n.type()).isEqualTo(NotificationType.ORDER);
        assertThat(n.message()).contains("30000");     // 얼마짜리가 취소됐는지
        assertThat(n.link()).isEqualTo("/orders/" + orderId);
    }

    // ── 반품 (2026-08-11, 08-10 §16-4 4번 — 여기가 통째로 비어 있었다) ──────

    /**
     * ⚠ 배송완료의 적립 문구와 <b>같은 분기</b>다 — 값이 0이면 안 넣는다.
     * 「0원이 환불되었습니다」는 안내가 아니라 혼란이라는 판단을 반품 쪽에도 그대로 적용한다.
     */
    @Test
    @DisplayName("반품 승인 → 구매자에게 **환불 금액과 함께** (돈이 움직였는데 말이 없으면 안 된다)")
    void returnedNotifiesBuyer() {
        handler.handle(new OrderReturnedEvent(orderId, buyerId, 25_000L, List.of()));

        Notified n = captured();
        assertThat(n.memberId()).isEqualTo(buyerId);
        assertThat(n.type()).isEqualTo(NotificationType.ORDER);
        assertThat(n.message()).contains("25000");
        assertThat(n.link()).isEqualTo("/orders/" + orderId);
    }

    @Test
    @DisplayName("⚠ 환불액이 0이면 금액 문구를 **넣지 않는다** (배송완료 적립과 같은 판단)")
    void returnedWithoutRefund() {
        handler.handle(new OrderReturnedEvent(orderId, buyerId, 0L, List.of()));

        Notified n = captured();
        assertThat(n.message()).isEqualTo("반품이 완료되었어요.");
        assertThat(n.message()).doesNotContain("환불");
    }

    /**
     * 🔴 셋 중 가장 급했던 자리 — 거절은 상태가 조용히 {@code DELIVERED} 로 돌아갈 뿐이라
     * 알림이 없으면 <b>요청해 놓고 영영 소식이 없다.</b>
     * ⚠ 사유를 못 넣는다({@code rejectReturn} 이 안 받는다) — <b>지어내지 않고</b> 링크로 보낸다.
     */
    @Test
    @DisplayName("반품 거절 → 구매자에게 알린다 (사유는 지어내지 않고 주문 상세로 보낸다)")
    void returnRejectedNotifiesBuyer() {
        handler.handle(new OrderReturnRejectedEvent(orderId, buyerId));

        Notified n = captured();
        assertThat(n.memberId()).isEqualTo(buyerId);
        assertThat(n.type()).isEqualTo(NotificationType.ORDER);
        assertThat(n.title()).contains("거절");
        assertThat(n.link()).isEqualTo("/orders/" + orderId);
    }
}
