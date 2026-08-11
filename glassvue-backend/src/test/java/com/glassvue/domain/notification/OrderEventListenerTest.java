package com.glassvue.domain.notification;

import static org.mockito.Mockito.verify;

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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

    @Mock OrderNotificationHandler notificationHandler;
    @InjectMocks OrderEventListener listener;

    @Test
    @DisplayName("리스너는 로직 없이 Handler에 위임만 한다")
    void delegatesToHandler() {
        OrderPlacedEvent event = new OrderPlacedEvent(UUID.randomUUID(), UUID.randomUUID(), 10_000, 1, List.of());
        listener.onOrderPlaced(event);
        verify(notificationHandler).handle(event);
    }

    @Test
    @DisplayName("취소 이벤트도 Handler에 위임만 한다")
    void delegatesCancelledToHandler() {
        OrderCancelledEvent event = new OrderCancelledEvent(UUID.randomUUID(), UUID.randomUUID(), 30_000, 2, List.of());
        listener.onOrderCancelled(event);
        verify(notificationHandler).handle(event);
    }

    /**
     * 배송완료도 마찬가지다 — 세 갈래 중 <b>이것만 빠져 있었다</b>(H-5 실측: 이 리스너 66%).
     * ⚠ 여기서 적립을 하지 않는 것도 규약이다 — 적립은 {@code OrderService.deliver()} 안에서
     * 동기로 끝나고, {@code @Async} 유실이 돈에 닿지 않게 한다({@link OrderDeliveredEvent} javadoc).
     */
    @Test
    @DisplayName("배송완료 이벤트도 Handler에 위임만 한다")
    void delegatesDeliveredToHandler() {
        OrderDeliveredEvent event = new OrderDeliveredEvent(UUID.randomUUID(), UUID.randomUUID(), 50_000, 500);
        listener.onOrderDelivered(event);
        verify(notificationHandler).handle(event);
    }

    @Test
    @DisplayName("반품 승인 이벤트도 Handler에 위임만 한다")
    void delegatesReturnedToHandler() {
        OrderReturnedEvent event = new OrderReturnedEvent(UUID.randomUUID(), UUID.randomUUID(), 25_000, List.of());
        listener.onOrderReturned(event);
        verify(notificationHandler).handle(event);
    }

    @Test
    @DisplayName("반품 거절 이벤트도 Handler에 위임만 한다")
    void delegatesReturnRejectedToHandler() {
        OrderReturnRejectedEvent event = new OrderReturnRejectedEvent(UUID.randomUUID(), UUID.randomUUID());
        listener.onOrderReturnRejected(event);
        verify(notificationHandler).handle(event);
    }

    /**
     * 🔴 <b>위 넷은 손으로 적은 목록이라 뒤처질 수 있다</b> — 실제로 그렇게 됐다.
     * {@code OrderReturnedEvent} 는 2026-07-24 부터 있었는데 <b>핸들러도 리스너도 없어</b>
     * 반품 승인이 아무에게도 안 알려졌다(08-10 §16-4 4번, 2026-08-11 확인).
     *
     * <p>⚠ <b>배선 누락은 조용하다.</b> 핸들러를 써 두고 리스너에 줄을 안 넣으면
     * 컴파일도 되고 테스트도 초록인데 <b>알림만 안 간다</b> — 아무도 «알림 고장» 으로 신고하지 않는다
     * (그런 알림이 있었다는 걸 모르니까).
     *
     * <p>→ 그래서 <b>핸들러의 {@code handle} 오버로드 전부</b>에 대해 리스너에 받는 메서드가 있는지
     * 확인한다. 오늘 §3-1·§3-4 와 같은 판단이다 — <b>목록을 손으로 지키지 않고 기계가 대조한다.</b>
     */
    @Test
    @DisplayName("🔴 Handler 의 handle 오버로드마다 리스너에 받는 메서드가 있다 (배선 누락 방지)")
    void everyHandledEventIsWired() {
        List<Class<?>> handled = java.util.Arrays.stream(OrderNotificationHandler.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("handle") && m.getParameterCount() == 1)
                .map(m -> m.getParameterTypes()[0])
                .toList();

        // ⚠ WA §3-3 — 「0건」이 «안 밟아서 0» 일 수 있다. 못 찾으면 이 테스트는 아무것도 검사하지 않는다.
        org.assertj.core.api.Assertions.assertThat(handled)
                .as("OrderNotificationHandler 에서 handle 메서드를 하나도 못 찾았다 — 검사가 헛돌았다")
                .isNotEmpty();

        List<Class<?>> wired = java.util.Arrays.stream(OrderEventListener.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(
                        org.springframework.transaction.event.TransactionalEventListener.class))
                .filter(m -> m.getParameterCount() == 1)
                .map(m -> m.getParameterTypes()[0])
                .toList();

        org.assertj.core.api.Assertions.assertThat(wired)
                .as("""
                        핸들러가 처리할 줄 아는 이벤트인데 리스너가 안 받는다 — **알림이 조용히 안 간다.**
                        → OrderEventListener 에 @TransactionalEventListener(AFTER_COMMIT) 메서드를 추가한다.""")
                .containsAll(handled);
    }
}
