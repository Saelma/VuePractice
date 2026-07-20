package com.glassvue.domain.notification;

import com.glassvue.domain.catalog.event.StockRunningLowEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 재고 이벤트 리스너(어댑터). OrderEventListener와 같은 규약 — 수신·위임만, 로직 없음.
 *
 * - {@code @TransactionalEventListener(AFTER_COMMIT)}: 재고 차감이 **커밋된 뒤에만** 알림.
 *   주문이 롤백되면 재고도 롤백되므로 "실제로 줄지 않은 재고"에 대한 오알림을 막는다.
 * - {@code @Async}: 알림 처리를 주문 요청 스레드에서 분리(이벤트 풀 event-*).
 */
@Component
@RequiredArgsConstructor
public class StockEventListener {

    private final StockAlertHandler stockAlertHandler;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockRunningLow(StockRunningLowEvent event) {
        stockAlertHandler.handle(event);
    }
}
