package com.glassvue.domain.restock;

import static org.mockito.Mockito.verify;

import com.glassvue.domain.catalog.event.StockReplenishedEvent;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RestockEventListenerTest {

    @Mock RestockNotificationHandler restockNotificationHandler;
    @InjectMocks RestockEventListener listener;

    @Test
    @DisplayName("리스너는 로직 없이 Handler에 위임만 한다")
    void delegatesToHandler() {
        StockReplenishedEvent event = new StockReplenishedEvent(UUID.randomUUID(), "무선키보드");
        listener.onStockReplenished(event);
        verify(restockNotificationHandler).handle(event);
    }
}
