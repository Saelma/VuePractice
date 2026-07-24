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
 */
public record OrderReturnedEvent(UUID orderId, UUID memberId, List<SoldLine> lines)
        implements DomainEvent {

    public static OrderReturnedEvent from(Order order) {
        return new OrderReturnedEvent(order.getId(), order.getMemberId(), SoldLine.from(order));
    }
}
