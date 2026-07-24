package com.glassvue.domain.notification;

import com.glassvue.domain.notification.entity.NotificationType;
import com.glassvue.domain.notification.service.NotificationCommandService;
import com.glassvue.domain.order.event.OrderCancelledEvent;
import com.glassvue.domain.order.event.OrderDeliveredEvent;
import com.glassvue.domain.order.event.OrderPlacedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 주문 알림 처리 — 이벤트에 반응하는 "진짜 주체". 전송수단(스프링 이벤트/RabbitMQ)과 무관한 순수 로직.
 * 리스너(OrderEventListener)가 위임하지만, 배치·다른 진입점에서 직접 호출해도 된다(재사용·테스트 용이).
 *
 * <p>2026-07-24: 로그 stub 을 걷고 <b>실제 인앱 알림</b>을 만든다({@link NotificationCommandService}).
 * 구매자에게 알림함 + SSE 푸시로 전달되고, {@code link} 로 누르면 그 주문 상세로 간다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationHandler {

    private final NotificationCommandService notificationService;

    public void handle(OrderPlacedEvent event) {
        notificationService.create(event.memberId(), NotificationType.ORDER,
                "주문이 접수되었어요",
                "상품 " + event.itemCount() + "건 주문이 접수되었습니다.",
                "/orders/" + event.orderId());
        log.info("[알림] 주문 접수 — order={} member={}", event.orderId(), event.memberId());
    }

    /** 배송완료 안내 — 적립 결과를 함께 알린다(적립 자체는 주문 트랜잭션에서 이미 끝났다). */
    public void handle(OrderDeliveredEvent event) {
        String message = event.earnedPoint() > 0
                ? "배송이 완료되었어요. " + event.earnedPoint() + "P 적립되었습니다."
                : "배송이 완료되었어요.";
        notificationService.create(event.memberId(), NotificationType.ORDER,
                "배송이 완료되었어요", message, "/orders/" + event.orderId());
        log.info("[알림] 배송완료 — order={} member={} earned={}P", event.orderId(), event.memberId(), event.earnedPoint());
    }

    public void handle(OrderCancelledEvent event) {
        notificationService.create(event.memberId(), NotificationType.ORDER,
                "주문이 취소되었어요",
                event.totalPrice() + "원 주문이 취소되었습니다.",
                "/orders/" + event.orderId());
        log.info("[알림] 주문 취소 — order={} member={}", event.orderId(), event.memberId());
    }
}
