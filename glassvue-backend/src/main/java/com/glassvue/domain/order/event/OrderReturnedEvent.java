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
 * <p>⚠ <b>고객 알림이 여기 붙는다</b> — 없으면 <b>돈이 환불됐는데 고객에게 아무 말이 없다.</b>
 * 취소는 알림이 가는데 반품은 안 가는 <b>비대칭</b>이었다({@code handoffs/2026-08-11}).
 *
 * <p>{@code refundedPoint} 를 실은 이유: 알림 문구가 «○○원이 적립금으로 환불되었습니다» 라고 말하려면
 * 그 값이 필요한데, 핸들러는 주문 엔티티를 못 본다(도메인 경계). 배송완료 이벤트가 {@code earnedPoint}
 * 를 싣는 것과 <b>같은 자리·같은 이유</b>다.
 *
 * @param itemsSummary   «지바 1개, 반팔티 1개» 처럼 사람이 읽는 요약 (감사 원장과 같은 문자열)
 * @param fullyReturned  🔴 이번 승인으로 <b>주문에 남은 것이 없어졌나</b>. 알림 문구가 «반품이
 *                       완료되었어요» 와 «일부 반품이 완료되었어요» 로 갈리는 유일한 근거다.
 *                       ⚠ 핸들러는 주문 상태를 못 보므로(도메인 경계) 이벤트가 실어 나른다.
 */
public record OrderReturnedEvent(UUID orderId, UUID memberId, String orderNo, long refundedPoint, List<SoldLine> lines,
                                 String itemsSummary, boolean fullyReturned)
        implements DomainEvent {

    /**
     * 🔴 <b>이번 회차에 실제로 되돌린 것만 싣는다</b> (2026-08-25, G-10).
     *
     * <p>⚠ <b>원본을 실으면 갈린다</b> — 전량 반품뿐이던 시절엔 «원본 = 이번에 되돌린 것» 이었지만,
     * 부분이 생기면 알림은 안 돌려준 금액을 말하고 판매량은 안 빠진 수량을 뺀다.
     * 그래서 호출부({@code OrderService.approveReturn})가 <b>정산이 실제로 낸 값</b>을 넘긴다.
     *
     * @param refundedPoint 이번 회차에 적립금으로 돌려준 금액 ({@code ReturnSettlement.refundAmount})
     * @param lines         이번 회차에 반품된 상품·수량
     */
    public static OrderReturnedEvent of(Order order, long refundedPoint, List<SoldLine> lines,
                                        String itemsSummary) {
        return new OrderReturnedEvent(order.getId(), order.getMemberId(), order.getOrderNo(), refundedPoint, lines,
                itemsSummary, order.hasNothingLeft());
    }
}
