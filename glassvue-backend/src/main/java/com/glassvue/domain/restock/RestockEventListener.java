package com.glassvue.domain.restock;

import com.glassvue.domain.catalog.event.StockReplenishedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 재입고 이벤트 리스너(어댑터). StockEventListener 와 같은 규약 — 수신·위임만, 로직 없음.
 *
 * <p>재고 이벤트를 <b>catalog</b> 가 내고 <b>restock</b> 이 받는다(도메인 경계는 이벤트로만 넘는다).
 * 재고 부족 알림(StockEventListener)은 notification 도메인이 받지만, 재입고는 신청(구독) 데이터를
 * 소유한 restock 이 반응하는 게 자연스러워 여기 둔다 — 발송 후 그 구독을 지우는 것까지 restock 관심사다.
 *
 * <ul>
 *   <li>{@code AFTER_COMMIT}: 재고 증가가 <b>커밋된 뒤에만</b> 알림. 주문 취소·상품 편집이 롤백되면
 *       재고도 롤백되므로 "실제로 안 들어온 재입고" 오알림을 막는다.</li>
 *   <li>{@code @Async}: 알림 처리를 원 요청 스레드에서 분리(이벤트 풀 event-*).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class RestockEventListener {

    private final RestockNotificationHandler restockNotificationHandler;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockReplenished(StockReplenishedEvent event) {
        restockNotificationHandler.handle(event);
    }
}
