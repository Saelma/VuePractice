package com.glassvue.domain.notification;

import com.glassvue.domain.order.event.OrderPlacedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 이벤트 리스너(어댑터). "수신·위임"만 하고 로직은 갖지 않는다 — 실제 처리는 Handler에 위임.
 *
 * - {@code @TransactionalEventListener(AFTER_COMMIT)}: 주문 트랜잭션이 **커밋된 뒤에만** 실행(롤백엔 반응 안 함).
 * - {@code @Async}: 커밋 후 처리를 **요청 스레드에서 떼어내 이벤트 풀 스레드(event-*)에서** 실행 → checkout 응답을 안 막음.
 *   "트랜잭션·스레딩 같은 전송 관심사"는 어댑터인 리스너가 흡수하고 Handler는 순수하게 둔다.
 * - 인프로세스 @Async는 best-effort(유실 가능). 유실 금지 요구 시 아웃박스/RabbitMQ(MSA 단계). 그때 리스너 자리에
 *   RabbitMQ 컨슈머가 들어오고 Handler는 재사용.
 */
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final OrderNotificationHandler notificationHandler;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        notificationHandler.handle(event);
    }
}
