package com.glassvue.domain.notification;

import static org.mockito.Mockito.verify;

import com.glassvue.domain.order.event.OrderCancelledEvent;
import com.glassvue.domain.order.event.OrderDeliveredEvent;
import com.glassvue.domain.order.event.OrderPlacedEvent;
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
}
