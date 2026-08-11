package com.glassvue.domain.notification;

import com.glassvue.domain.order.event.OrderCancelledEvent;
import com.glassvue.domain.order.event.OrderDeliveredEvent;
import com.glassvue.domain.order.event.OrderPlacedEvent;
import com.glassvue.domain.order.event.OrderReturnRejectedEvent;
import com.glassvue.domain.order.event.OrderReturnedEvent;
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

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent event) {
        notificationHandler.handle(event);
    }

    /**
     * 배송완료 — <b>알림만</b> 한다. 적립은 이미 {@code OrderService.deliver()} 안에서 동기로 끝났다.
     * 여기서 적립하면 @Async 유실 시 고객 돈이 사라진다(OrderDeliveredEvent javadoc 참조).
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderDelivered(OrderDeliveredEvent event) {
        notificationHandler.handle(event);
    }

    /**
     * 반품 승인·거절 (2026-08-11, 08-10 §16-4 4번).
     *
     * <p>⚠ <b>이 둘이 통째로 빠져 있었다.</b> 취소·배송완료는 여기 줄이 있는데 반품만 없었고,
     * 그래서 «돈이 환불됐는데 아무 말이 없다»·«요청해 놓고 소식이 없다» 가 됐다.
     * ⚠ 이 리스너의 <b>메서드 목록이 곧 「고객에게 알리는 주문 사건」의 목록</b>이다 —
     * 주문에 새 사건이 생기면 여기 줄이 있는지부터 본다(취소·반품의 되돌리기 목록이
     * {@code OrderService.applyCancellation} 인 것과 같은 자리, §8).
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderReturned(OrderReturnedEvent event) {
        notificationHandler.handle(event);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderReturnRejected(OrderReturnRejectedEvent event) {
        notificationHandler.handle(event);
    }
}
