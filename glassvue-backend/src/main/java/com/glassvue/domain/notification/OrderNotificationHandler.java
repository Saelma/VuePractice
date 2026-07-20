package com.glassvue.domain.notification;

import com.glassvue.domain.order.event.OrderCancelledEvent;
import com.glassvue.domain.order.event.OrderPlacedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 주문 알림 처리 — 이벤트에 반응하는 "진짜 주체". 전송수단(스프링 이벤트/RabbitMQ)과 무관한 순수 로직.
 * 리스너(OrderEventListener)가 위임하지만, 배치·다른 진입점에서 직접 호출해도 된다(재사용·테스트 용이).
 * 지금은 로그 stub — 실제 메일/푸시는 이후 단계.
 */
@Slf4j
@Component
public class OrderNotificationHandler {

    public void handle(OrderPlacedEvent event) {
        log.info("[알림] 주문 확인 발송(stub) — order={} member={} amount={} items={}",
                event.orderId(), event.memberId(), event.totalPrice(), event.itemCount());
    }

    public void handle(OrderCancelledEvent event) {
        log.info("[알림] 주문 취소 안내 발송(stub) — order={} member={} amount={} items={}",
                event.orderId(), event.memberId(), event.totalPrice(), event.itemCount());
    }
}
