package com.glassvue.domain.notification;

import static org.mockito.Mockito.verify;

import com.glassvue.domain.order.event.OrderPlacedEvent;
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
        OrderPlacedEvent event = new OrderPlacedEvent(UUID.randomUUID(), UUID.randomUUID(), 10_000, 1);
        listener.onOrderPlaced(event);
        verify(notificationHandler).handle(event);
    }
}
