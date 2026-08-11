package com.glassvue.domain.catalog.event;

import static org.mockito.Mockito.verify;

import com.glassvue.domain.catalog.service.command.SalesSyncHandler;
import com.glassvue.domain.order.event.OrderCancelledEvent;
import com.glassvue.domain.order.event.OrderPlacedEvent;
import com.glassvue.domain.order.event.OrderReturnedEvent;
import com.glassvue.domain.order.event.SoldLine;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SalesEventListenerTest {

    @Mock SalesSyncHandler salesSyncHandler;
    @InjectMocks SalesEventListener listener;

    private final List<SoldLine> lines = List.of(new SoldLine(UUID.randomUUID(), 3));

    @Test
    @DisplayName("주문됨 → 판매량 증가에 위임한다")
    void placedIncreases() {
        listener.onOrderPlaced(new OrderPlacedEvent(UUID.randomUUID(), UUID.randomUUID(), 10_000, 1, lines));
        verify(salesSyncHandler).increase(lines);
    }

    @Test
    @DisplayName("취소 → 판매량 감소에 위임한다(주문의 반대)")
    void cancelledDecreases() {
        listener.onOrderCancelled(new OrderCancelledEvent(UUID.randomUUID(), UUID.randomUUID(), 10_000, 1, lines));
        verify(salesSyncHandler).decrease(lines);
    }

    @Test
    @DisplayName("반품 승인 → 판매량 감소에 위임한다")
    void returnedDecreases() {
        // refundedPoint 는 알림 문구용이라 catalog 는 안 본다 — 0 이든 아니든 판매량 감소는 같다.
        listener.onOrderReturned(new OrderReturnedEvent(UUID.randomUUID(), UUID.randomUUID(), 0L, lines));
        verify(salesSyncHandler).decrease(lines);
    }
}
