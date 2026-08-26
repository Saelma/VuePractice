package com.glassvue.domain.order.event;

import com.glassvue.domain.order.entity.Order;
import com.glassvue.global.messaging.DomainEvent;
import java.util.UUID;

/**
 * 배송완료 도메인 이벤트 (2026-07-24, 백로그 C-10).
 *
 * <p>⚠ <b>이 이벤트가 적립을 하는 게 아니다.</b> 적립은 {@code OrderService.deliver()} 안에서
 * <b>동기로</b> 끝나고, 이 이벤트는 그 <b>결과를 알리는</b> 용도다.
 *
 * <h3>왜 적립을 이벤트로 빼지 않았나</h3>
 * <p>이 프로젝트의 리스너는 {@code @Async @TransactionalEventListener(AFTER_COMMIT)} 이라
 * <b>인프로세스 best-effort</b>다 — 유실될 수 있다. 알림이 유실되면 메일 한 통을 놓치지만,
 * <b>적립이 유실되면 고객의 돈이 사라진다.</b> 되돌릴 방법도, 유실됐다는 걸 알 방법도 없다.
 *
 * <p>같은 판단이 이미 있다 — <b>재고 복원</b>은 취소 처리의 일부(동기 성공 필수)라
 * 이벤트로 빼지 않았다(ARCHITECTURE §6 도입 조건 표). 적립금도 같은 쪽이다.
 * 유실 금지를 이벤트로 보장하려면 아웃박스/RabbitMQ 가 필요하고, 그건 MSA 단계다.
 *
 * @param payableAmount 적립·등급 산정 기준액(상품합계 − 쿠폰할인 − 사용 적립금)
 * @param earnedPoint   실제로 적립된 금액. 알림이 "500P 적립되었습니다"를 쓸 수 있게 담는다
 */
public record OrderDeliveredEvent(UUID orderId, UUID memberId, String orderNo,
                                  long payableAmount, long earnedPoint) implements DomainEvent {

    public static OrderDeliveredEvent from(Order order, long earnedPoint) {
        return new OrderDeliveredEvent(order.getId(), order.getMemberId(), order.getOrderNo(),
                order.rewardableAmount(), earnedPoint);
    }
}
