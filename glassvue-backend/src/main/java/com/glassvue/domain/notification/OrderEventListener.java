package com.glassvue.domain.notification;

import com.glassvue.domain.order.event.OrderPlacedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 이벤트 리스너(어댑터). "수신·위임"만 하고 로직은 갖지 않는다 — 실제 처리는 Handler에 위임.
 *
 * - {@code @TransactionalEventListener(AFTER_COMMIT)}: 주문 트랜잭션이 **커밋된 뒤에만** 실행
 *   (롤백된 주문엔 반응 안 함). 이 "전송/트랜잭션 관심사"는 어댑터인 리스너가 흡수하고 Handler는 순수하게 둔다.
 * - MSA 단계에선 이 리스너 자리에 RabbitMQ 컨슈머가 들어오고, Handler는 그대로 재사용된다.
 */
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final OrderNotificationHandler notificationHandler;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        notificationHandler.handle(event);
    }
}
