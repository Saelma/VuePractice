package com.glassvue.domain.notification;

import com.glassvue.domain.order.event.OrderPlacedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 이벤트 구독자(notification 도메인). order 도메인은 이 리스너의 존재를 모른다.
 *
 * - {@code @TransactionalEventListener(AFTER_COMMIT)}: 주문 트랜잭션이 **커밋된 뒤에만** 실행.
 *   결제/주문이 롤백되면 알림도 안 나간다(직접 호출로는 얻기 어려운 정확성 이점).
 * - 지금은 로그 stub — 실제 메일/푸시/포인트 적립은 이후 단계. 새 반응은 리스너만 추가하면 되고
 *   OrderService(발행부)는 건드리지 않는다(fan-out).
 * - MSA 단계에선 이 이벤트가 RabbitMQ 메시지가 되고, 리스너는 별도 서비스의 컨슈머가 된다.
 */
@Slf4j
@Component
public class OrderNotificationListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        log.info("[알림] 주문 확인 발송(stub) — order={} member={} amount={} items={}",
                event.orderId(), event.memberId(), event.totalPrice(), event.itemCount());
    }
}
