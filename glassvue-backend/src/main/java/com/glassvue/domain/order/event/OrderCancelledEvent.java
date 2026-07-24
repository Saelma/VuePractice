package com.glassvue.domain.order.event;

import com.glassvue.domain.order.entity.Order;
import com.glassvue.global.messaging.DomainEvent;
import java.util.List;
import java.util.UUID;

/**
 * 주문 취소 도메인 이벤트. OrderPlacedEvent와 같은 규약 — 발행 도메인(order)이 소유하고,
 * order는 구독자(알림·정산·포인트 회수 등)를 모른다.
 *
 * 재고 복원은 catalog가 자체적으로 처리하므로 이 이벤트에 담지 않는다(취소 처리의 일부지 후처리가 아님).
 *
 * <p>{@code lines} 는 상품별 수량 — catalog 가 판매량 비정규화에서 <b>되돌리는</b> 데 쓴다(주문의 반대, B-8).
 */
public record OrderCancelledEvent(UUID orderId, UUID memberId, long totalPrice, int itemCount,
                                  List<SoldLine> lines) implements DomainEvent {

    public static OrderCancelledEvent from(Order order) {
        return new OrderCancelledEvent(order.getId(), order.getMemberId(), order.getTotalPrice(),
                order.getItems().size(), SoldLine.from(order));
    }
}
