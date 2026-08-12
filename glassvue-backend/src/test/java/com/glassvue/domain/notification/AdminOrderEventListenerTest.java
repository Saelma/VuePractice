package com.glassvue.domain.notification;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.glassvue.domain.order.event.OrderReturnRequestedEvent;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 관리자 대상 주문 이벤트 리스너 (2026-08-12, 08-11 이월).
 *
 * <p>{@link OrderEventListenerTest} 와 같은 것을 본다 — <b>리스너는 위임만 한다</b>.
 * 나눠 둔 이유는 «누가 받나» 이고, 그 경계는 {@link AdminOrderEventListener} javadoc 에 있다.
 */
@ExtendWith(MockitoExtension.class)
class AdminOrderEventListenerTest {

    @Mock ReturnRequestAlertHandler returnRequestAlertHandler;
    @InjectMocks AdminOrderEventListener listener;

    @Test
    @DisplayName("반품 요청 이벤트를 Handler 에 위임만 한다")
    void delegatesReturnRequestedToHandler() {
        OrderReturnRequestedEvent event = new OrderReturnRequestedEvent(
                UUID.randomUUID(), UUID.randomUUID(), "20260812-0001", "ZZ구매자", "변심");

        listener.onOrderReturnRequested(event);

        verify(returnRequestAlertHandler).handle(event);
        // ⚠ 리스너가 로직을 갖지 않는다는 것까지 본다 — 위임 «만» 이 규약이다(이벤트 3층).
        verifyNoMoreInteractions(returnRequestAlertHandler);
    }
}
