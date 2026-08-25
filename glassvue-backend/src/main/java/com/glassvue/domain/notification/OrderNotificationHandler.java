package com.glassvue.domain.notification;

import com.glassvue.domain.notification.entity.NotificationType;
import com.glassvue.domain.notification.service.NotificationCommandService;
import com.glassvue.domain.order.event.OrderCancelledEvent;
import com.glassvue.domain.order.event.OrderItemCancelledEvent;
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
        // ⚠ **«이번에 취소된 금액» 이다**(2026-08-25, §I). 예전엔 주문 **원본** 을 말해서, 부분 취소로
        //    이미 25,000 이 빠진 주문의 남은 10,000 을 취소하며 «35,000원 취소» 라고 알렸다(5296 실측).
        notificationService.create(event.memberId(), NotificationType.ORDER,
                "주문이 취소되었어요",
                event.cancelledAmount() + "원 주문이 취소되었습니다.",
                "/orders/" + event.orderId());
        log.info("[알림] 주문 취소 — order={} member={} amount={}",
                event.orderId(), event.memberId(), event.cancelledAmount());
    }

    /**
     * 🔴 <b>부분 취소</b> — 고객에게 «무엇이 몇 개 빠졌고 얼마가 돌아왔나» 를 알린다 (2026-08-25, §I).
     *
     * <p>⚠ <b>여기까지 알림이 아예 없었다.</b> 관리자가 대신 부분 취소하면 고객에게 <b>아무 말이
     * 없었다</b> — 전체 취소는 알림이 가므로 비대칭이었고, 2026-08-11 이 반품에 대해 고친 것의
     * <b>거울상</b>이다. G-4(08-24)가 두고 간 자리다.
     *
     * <p>⚠ <b>제목이 «주문이 취소되었어요» 가 아니다</b> — 1개만 빠졌는데 그렇게 말하면 고객은
     * 주문 전체가 취소된 줄 안다. 전량이 빠지면 그때 {@code OrderCancelledEvent} 가 따로 나간다.
     */
    public void handle(OrderItemCancelledEvent event) {
        // 🔴 **전량이 빠졌으면 여기서 말하지 않는다** — 뒤이어 OrderCancelledEvent 가 나가므로
        //    «주문 일부가 취소되었어요» 와 «주문이 취소되었어요» 가 **연달아 두 번** 뜬다.
        //    ⚠ 이벤트 자체를 안 낼 수는 없다: 판매량 되돌림이 이 회차 몫을 여기에만 싣는다.
        //       **구독자마다 «무엇을 하느냐» 를 가르는** 자리다(catalog 는 언제나 처리한다).
        if (event.orderFullyCancelled()) {
            log.info("[알림] 부분 취소 생략(주문 전체 취소로 이어짐) — order={}", event.orderId());
            return;
        }
        StringBuilder message = new StringBuilder(event.itemsSummary()).append(" 취소되었습니다.");
        if (event.refundedAmount() > 0) {
            message.append(' ').append(event.refundedAmount()).append("원");
            // 적립금으로도 돌아갔으면 둘 다 말한다 — «얼마 돌아왔나» 가 한쪽만이면 고객이 잔액과 못 맞춘다.
            if (event.refundedPoint() > 0) {
                message.append("과 적립금 ").append(event.refundedPoint()).append("원");
            }
            message.append("이 환불됩니다.");
        } else if (event.refundedPoint() > 0) {
            message.append(' ').append(event.refundedPoint()).append("원이 적립금으로 환불되었습니다.");
        }
        notificationService.create(event.memberId(), NotificationType.ORDER,
                "주문 일부가 취소되었어요", message.toString(), "/orders/" + event.orderId());
        log.info("[알림] 부분 취소 — order={} member={} refund={} point={}",
                event.orderId(), event.memberId(), event.refundedAmount(), event.refundedPoint());
    }

    /**
     * 반품 승인 — 환불 결과를 함께 알린다 (2026-08-11, 08-10 §16-4 4번).
     * 환불 자체는 {@code approveReturn} 안에서 이미 끝났다({@link #handle(OrderDeliveredEvent)} 과 같은 규약).
     *
     * <p>⚠ 여기까지 <b>알림이 아예 없었다</b> — 돈이 환불됐는데 고객이 알 방법이 주문 상세뿐이었다.
     * 취소에는 있고 반품에는 없던 <b>비대칭</b>이고, 같은 날 §8(쿠폰)에서 본 것과 같은 모양이다.
     */
    public void handle(OrderReturnedEvent event) {
        // 🔴 **부분 반품에도 «반품이 완료되었어요» 라고 말하던 자리다** (2026-08-25, §8-6).
        //    G-10 검증에서 **네 번** 거짓으로 나갔다 — 고객은 주문 전체가 반품된 줄 읽는다.
        //    ⚠ 흐름에 «부분» 을 넣고 **문구를 안 열었다**: WA §1-2-1 «남기는 쪽 ↔ 보는 쪽» 그대로다.
        //    🔴 그 짝이 «조용해서 오래 간다» 고 경고한 그대로 **돈은 정확했고 아무것도 안 터졌다.**
        String title = event.fullyReturned() ? "반품이 완료되었어요" : "일부 반품이 완료되었어요";
        String what = event.fullyReturned()
                ? "반품이 완료되었어요."
                : event.itemsSummary() + " 반품이 완료되었어요.";
        String message = event.refundedPoint() > 0
                ? what + " " + event.refundedPoint() + "원이 적립금으로 환불되었습니다."
                : what;
        notificationService.create(event.memberId(), NotificationType.ORDER,
                title, message, "/orders/" + event.orderId());
        log.info("[알림] 반품 승인 — order={} member={} refunded={} full={}", event.orderId(),
                event.memberId(), event.refundedPoint(), event.fullyReturned());
    }

    /**
     * 반품 거절 (2026-08-11) — <b>셋 중 가장 급한 자리였다.</b> 승인은 적립금이 들어와 고객이
     * 눈치챌 수라도 있지만, 거절은 상태가 조용히 {@code DELIVERED} 로 돌아갈 뿐이라
     * <b>요청해 놓고 영영 소식이 없다.</b>
     *
     * <p>🔴 <b>사유를 문구에 담는다</b>(2026-08-11, V47). 처음엔 «자세한 내용은 주문 상세에서
     * 확인해 주세요» 였는데 <b>그 상세에 아무것도 없었다</b> — 알림이 <b>없는 곳을 가리켰다.</b>
     * ⚠ «없는 값을 지어내지 않는다» 를 지키느라 문장을 비웠지만, 그 결과가 <b>더 나쁜 안내</b>였다.
     * 값이 없으면 문장을 비울 게 아니라 <b>값을 만들어야</b> 했다.
     *
     * <p>⚠ 사유는 필수라 {@code null} 이 올 수 없지만, 옛 jar 가 만든 이벤트가 큐에 남는 등의
     * 경계는 방어해 둔다 — <b>«null» 이라는 글자가 고객 알림에 뜨는 것</b>보다는 낫다.
     */
    public void handle(OrderReturnRejectedEvent event) {
        String message = (event.reason() == null || event.reason().isBlank())
                ? "자세한 내용은 고객센터로 문의해 주세요."
                : "사유: " + event.reason();
        notificationService.create(event.memberId(), NotificationType.ORDER,
                "반품 요청이 거절되었어요", message, "/orders/" + event.orderId());
        log.info("[알림] 반품 거절 — order={} member={}", event.orderId(), event.memberId());
    }
}
