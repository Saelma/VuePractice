package com.glassvue.domain.order.event;

import com.glassvue.domain.order.entity.Order;
import com.glassvue.global.messaging.DomainEvent;
import java.util.List;
import java.util.UUID;

/**
 * 주문 생성(checkout) 도메인 이벤트. 발행 도메인(order)이 소유하는 공개 계약(DomainEvent).
 * 다른 도메인은 이 이벤트를 구독해 후처리(알림·포인트 등)한다 — order는 구독자를 모른다.
 * 지금은 스프링 ApplicationEventPublisher로 발행하지만, MSA 단계에선 이 이벤트가 메시지(RabbitMQ)로 승격된다.
 *
 * <p>{@code lines} 는 상품별 판매 수량 — catalog 가 인기(판매량)를 비정규화하는 데 쓴다(B-8, {@link SoldLine}).
 * 알림 구독자는 이 필드를 무시하면 된다.
 */
public record OrderPlacedEvent(UUID orderId, UUID memberId, long totalPrice, int itemCount,
                               List<SoldLine> lines) implements DomainEvent {

    public static OrderPlacedEvent from(Order order) {
        return new OrderPlacedEvent(order.getId(), order.getMemberId(), order.getTotalPrice(),
                order.getItems().size(), SoldLine.from(order));
    }
}
