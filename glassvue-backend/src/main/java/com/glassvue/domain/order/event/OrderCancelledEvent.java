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
 *
 * <p>🔴 <b>{@code cancelledAmount} 는 «이번에 취소된 금액» 이지 주문 원금이 아니다</b> — 부분 취소
 * <b>뒤</b> 전체 취소에서 갈린다(원본을 쓰면 이미 빠진 몫까지 다시 알린다).
 * ⚠ 같은 이벤트의 {@code lines} 도 같은 뜻인데 <b>두 필드가 따로 고쳐졌다</b> —
 * 한 이벤트 안에서도 <b>필드마다</b> 물어야 한다(WA §2-2-2 의 이벤트 판).
 * 실측 경위는 {@code handoffs/2026-08-25} §I.
 */
public record OrderCancelledEvent(UUID orderId, UUID memberId, String orderNo, long cancelledAmount, int itemCount,
                                  List<SoldLine> lines) implements DomainEvent {

    /**
     * 🔴 <b>부분 취소가 마지막 품목까지 비웠을 때</b> (2026-08-26, BACKLOG I-12).
     *
     * <p>⚠ <b>{@link #from} 을 쓸 수 없다.</b> 그쪽은 {@code order.remainingItemsTotal()} 을 읽는데,
     * 이 갈래에서는 <b>직전에 그 품목이 이미 빠져</b> 남은 것이 <b>0</b> 이다 → 고객에게
     * «<b>0원</b> 주문이 취소되었습니다» 가 나간다.
     *
     * <p>🔴 <b>그 금액을 알릴 곳이 여기뿐이다</b> — 마지막 회차는 «전량이 빠지는 경우» 라
     * <b>부분 취소 알림을 건너뛴다</b>(알림이 두 번 뜨는 것을 막으려고). 앞 회차들은 각자 금액을
     * 알렸으므로 여기서 빠뜨리면 <b>마지막 회차 몫만 통째로 사라진다.</b>
     *
     * <p>→ <b>이번 회차에 빠진 상품합계를 그대로 싣는다.</b> 경위는 {@code handoffs/2026-08-26} §I-12.
     * ⚠ 누적 환불액이 아니다 — 앞 회차는 이미 각자 알렸으므로 합치면 «또 받나» 로 읽힌다.
     * ⚠ 필드의 뜻은 {@link #from} 과 <b>같다</b>(«이번에 취소된 상품합계») — 경로마다 다른 뜻을
     * 넣지 않는다. 다른 것은 <b>그 값을 어디서 얻느냐</b> 뿐이다.
     */
    public static OrderCancelledEvent ofItemsDrained(Order order, long cancelledAmount) {
        return new OrderCancelledEvent(order.getId(), order.getMemberId(), order.getOrderNo(), cancelledAmount,
                order.getItems().size(), SoldLine.remaining(order));
    }

    public static OrderCancelledEvent from(Order order) {
        return new OrderCancelledEvent(order.getId(), order.getMemberId(), order.getOrderNo(),
                // 🔴 **«남은» 상품합계다** — 이번 취소로 실제로 빠지는 금액이 그것이다.
                order.remainingItemsTotal(),
                // 🔴 **«남은» 수량이다** (2026-08-25, G-10). 부분 취소가 이미 자기 몫을 되돌렸으므로
                //    여기서 원본을 빼면 판매량이 **두 번** 줄어든다 — 08-24 의 재고·적립금 사고와 같은 모양.
                order.getItems().size(), SoldLine.remaining(order));
    }
}
