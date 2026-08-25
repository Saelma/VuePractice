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
/**
 * ⚠ <b>{@code cancelledAmount} 는 «이번에 취소된 금액» 이지 주문 원금이 아니다</b> (2026-08-25, §I).
 *
 * <p>🔴 예전 이름은 {@code totalPrice} 였고 값도 {@code order.getTotalPrice()}(원본)였다. 부분 취소가
 * 생기기 전에는 «원본 = 이번에 취소된 것» 이라 구별할 이유가 없었는데, <b>부분 취소 뒤 전체 취소</b>
 * 에서 갈렸다 — 실측(2026-08-24 `20260824-5296`): 원본 35,000 중 25,000 이 이미 빠진 뒤
 * 남은 <b>10,000</b> 을 취소하면서 알림이 «<b>35,000원</b> 주문이 취소되었습니다» 라고 말했다.
 * `20260824-5297` 은 그 시점에 취소된 것이 <b>0원어치</b>인데 «25,000원» 이라 했다.
 * <p>⚠ 🔴 <b>같은 이벤트의 {@code lines} 는 08-25 에 «남은 것» 으로 고쳤는데 이 필드는 안 봤다</b> —
 * 한 이벤트 안에서도 <b>필드마다</b> 물어야 한다(WA §2-2-2 의 이벤트 판).
 */
public record OrderCancelledEvent(UUID orderId, UUID memberId, long cancelledAmount, int itemCount,
                                  List<SoldLine> lines) implements DomainEvent {

    public static OrderCancelledEvent from(Order order) {
        return new OrderCancelledEvent(order.getId(), order.getMemberId(),
                // 🔴 **«남은» 상품합계다** — 이번 취소로 실제로 빠지는 금액이 그것이다.
                order.remainingItemsTotal(),
                // 🔴 **«남은» 수량이다** (2026-08-25, G-10). 부분 취소가 이미 자기 몫을 되돌렸으므로
                //    여기서 원본을 빼면 판매량이 **두 번** 줄어든다 — 08-24 의 재고·적립금 사고와 같은 모양.
                order.getItems().size(), SoldLine.remaining(order));
    }
}
