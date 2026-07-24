package com.glassvue.domain.notification;

import static org.mockito.Mockito.verify;

import com.glassvue.domain.catalog.event.StockRunningLowEvent;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockEventListenerTest {

    @Mock StockAlertHandler stockAlertHandler;
    @InjectMocks StockEventListener listener;

    @Test
    @DisplayName("리스너는 로직 없이 Handler에 위임만 한다")
    void delegatesToHandler() {
        StockRunningLowEvent event = new StockRunningLowEvent(UUID.randomUUID(), "무선키보드", "검정/M", 3, 5);
        listener.onStockRunningLow(event);
        verify(stockAlertHandler).handle(event);
    }
}
