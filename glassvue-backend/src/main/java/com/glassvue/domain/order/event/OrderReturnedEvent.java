package com.glassvue.domain.order.event;

import com.glassvue.domain.order.entity.Order;
import com.glassvue.global.messaging.DomainEvent;
import java.util.List;
import java.util.UUID;

/**
 * 반품 승인(확정) 도메인 이벤트 (2026-07-24, B-8). OrderCancelledEvent와 같은 규약.
 *
 * <p>재고 복원·적립금 환불은 {@code OrderService.approveReturn()} 안에서 <b>동기로</b> 끝난다 —
 * 이 이벤트는 그 결과를 알리는 용도다(배송완료 이벤트가 적립을 시키지 않는 것과 같은 판단).
 * catalog 는 {@code lines} 로 판매량을 <b>되돌린다</b>(반품된 만큼 인기에서 뺀다).
 *
 * <p>⚠ <b>2026-08-11 까지 구독자가 catalog 하나뿐이었다</b> — 즉 <b>돈이 환불됐는데 고객에게
 * 아무 말이 없었다</b>(08-10 §16-4 4번). 취소는 알림이 가는데 반품은 안 가는 <b>비대칭</b>이었고,
 * 오늘 쿠폰(§8)과 <b>같은 모양</b>이다: 취소 쪽에만 줄이 있고 반품 쪽엔 없었다.
 *
 * <p>{@code refundedPoint} 를 실은 이유: 알림 문구가 «○○원이 적립금으로 환불되었습니다» 라고 말하려면
 * 그 값이 필요한데, 핸들러는 주문 엔티티를 못 본다(도메인 경계). 배송완료 이벤트가 {@code earnedPoint}
 * 를 싣는 것과 <b>같은 자리·같은 이유</b>다.
 */
public record OrderReturnedEvent(UUID orderId, UUID memberId, long refundedPoint, List<SoldLine> lines)
        implements DomainEvent {

    public static OrderReturnedEvent from(Order order) {
        return new OrderReturnedEvent(order.getId(), order.getMemberId(),
                order.refundableAmount(), SoldLine.from(order));
    }
}
