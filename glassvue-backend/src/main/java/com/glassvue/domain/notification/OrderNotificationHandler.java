package com.glassvue.domain.notification;

import com.glassvue.domain.notification.entity.NotificationType;
import com.glassvue.domain.notification.service.NotificationCommandService;
import com.glassvue.domain.order.event.OrderCancelledEvent;
import com.glassvue.domain.order.event.OrderDeliveredEvent;
import com.glassvue.domain.order.event.OrderPlacedEvent;
import com.glassvue.domain.order.event.OrderReturnRejectedEvent;
import com.glassvue.domain.order.event.OrderReturnedEvent;
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

    /**
     * 반품 승인 — 환불 결과를 함께 알린다 (2026-08-11, 08-10 §16-4 4번).
     * 환불 자체는 {@code approveReturn} 안에서 이미 끝났다({@link #handle(OrderDeliveredEvent)} 과 같은 규약).
     *
     * <p>⚠ 여기까지 <b>알림이 아예 없었다</b> — 돈이 환불됐는데 고객이 알 방법이 주문 상세뿐이었다.
     * 취소에는 있고 반품에는 없던 <b>비대칭</b>이고, 같은 날 §8(쿠폰)에서 본 것과 같은 모양이다.
     */
    public void handle(OrderReturnedEvent event) {
        String message = event.refundedPoint() > 0
                ? "반품이 완료되었어요. " + event.refundedPoint() + "원이 적립금으로 환불되었습니다."
                : "반품이 완료되었어요.";
        notificationService.create(event.memberId(), NotificationType.ORDER,
                "반품이 완료되었어요", message, "/orders/" + event.orderId());
        log.info("[알림] 반품 승인 — order={} member={} refunded={}", event.orderId(), event.memberId(),
                event.refundedPoint());
    }

    /**
     * 반품 거절 (2026-08-11) — <b>셋 중 가장 급한 자리였다.</b> 승인은 적립금이 들어와 고객이
     * 눈치챌 수라도 있지만, 거절은 상태가 조용히 {@code DELIVERED} 로 돌아갈 뿐이라
     * <b>요청해 놓고 영영 소식이 없다.</b>
     *
     * <p>⚠ 사유를 문구에 못 넣는다 — {@code Order.rejectReturn()} 이 사유를 안 받는다.
     * <b>없는 값을 지어내지 않고</b> 링크로 보낸다(취소 사유가 B-17 에서 뒤늦게 붙은 것과 같은 자리).
     */
    public void handle(OrderReturnRejectedEvent event) {
        notificationService.create(event.memberId(), NotificationType.ORDER,
                "반품 요청이 거절되었어요",
                "자세한 내용은 주문 상세에서 확인해 주세요.",
                "/orders/" + event.orderId());
        log.info("[알림] 반품 거절 — order={} member={}", event.orderId(), event.memberId());
    }
}
