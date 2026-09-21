package com.glassvue.domain.order.event;

import com.glassvue.domain.order.entity.Order;
import com.glassvue.global.messaging.DomainEvent;
import java.util.UUID;

/**
 * 반품 <b>요청</b> 도메인 이벤트 (2026-08-12, 08-11 이월).
 *
 * <p>🔴 <b>이 셋 중 마지막 하나였다.</b> 2026-08-11 에 승인·거절 알림을 붙이면서 요청은
 * <b>범위 밖으로 뒀고</b>(사용자 결정), 그대로 이월에 남아 두 번째 문서까지 내려왔다.
 *
 * <p>🔴 <b>앞의 둘과 방향이 반대다.</b> 승인·거절은 <b>구매자에게</b> 가지만 요청은
 * <b>관리자에게</b> 간다 — 그래서 리스너도 핸들러도 자리가 다르다
 * ({@code AdminOrderEventListener} · {@code ReturnRequestAlertHandler}).
 * ⚠ 이것이 «반품 알림» 을 한 덩어리로 세면 안 되는 이유다: <b>같은 사건의 알림이라도
 * 받는 사람이 다르면 다른 기능</b>이다.
 *
 * <p>⚠ <b>{@code lines} 는 여전히 없다.</b> 요청 단계에서는 재고도 적립금도 <b>안 움직인다</b>
 * (승인 때 움직인다 — {@code OrderService.approveReturn}). 판매량을 되돌릴 구독자가 없으므로
 * 없는 필드를 «짝이니까» 로 넣지 않는다({@link OrderReturnRejectedEvent} 와 같은 판단).
 *
 * <p>🔴 <b>그런데 {@code itemsSummary} 는 싣는다</b> (2026-09-21, BACKLOG §I-11). 위 규칙과
 * 어긋나지 않는다 — 그 규칙은 «쓸 구독자가 없는 필드를 넣지 말라» 이고, <b>여기는 쓸 구독자가 생겼다</b>:
 * 알림 문구가 «무엇을 몇 개» 를 말해야 관리자가 <b>열어 보기 전에</b> 규모를 안다.
 * ⚠ <b>부분 반품(G-10) 이 생기기 전에는 필요가 없었다</b> — 요청은 늘 전량이라 주문번호만으로 충분했다.
 * 부분이 생기면서 «반품 요청» 이 <b>몇 개짜리인지가 정보</b>가 됐다.
 * ⚠ {@code lines}(기계가 읽는 상품·수량)가 아니라 <b>사람이 읽는 한 줄</b>이다 —
 * {@link OrderReturnedEvent#itemsSummary()} 와 <b>같은 성격·같은 문자열</b>이고,
 * 식은 {@code Order.requestedReturnSummary()} <b>한 곳</b>에 있다.
 *
 * <p>⚠ <b>{@code memberId} 는 「요청한 사람」이지 「알림 받을 사람」이 아니다.</b> 앞의 둘에서는
 * 그 둘이 같았는데 여기서는 다르다 — 받는 쪽은 관리자 전원이라 <b>이벤트가 대상을 모른다.</b>
 * 대상 선정은 {@code MemberService.adminIds()} 가 하고 이벤트는 «무슨 일이 있었나» 만 싣는다.
 *
 * @param orderNo        사람이 읽는 주문번호 — 알림 문구가 «어느 주문인지» 를 말해야 관리자가 고른다.
 * @param buyerNickname  요청한 사람. ⚠ <b>스냅샷</b>이라 나중에 닉네임이 바뀌어도 알림은 그대로다
 *                       (알림은 «그때 무슨 일이 있었나» 의 기록이다).
 * @param reason         반품 사유. 선택값이라 <b>비어 있을 수 있다</b> — 문구에서 갈라 준다.
 * @param itemsSummary   «몽쉘 (L) 2개, 반팔티 1개» 처럼 사람이 읽는 이번 회차 요약.
 *                       ⚠ <b>비어 있을 수 있다</b>(고른 것이 없는 요청은 검증이 막지만, 이벤트는 그걸
 *                       전제하지 않는다) — 문구에서 갈라 준다. 사유와 <b>같은 규칙</b>이다.
 */
public record OrderReturnRequestedEvent(
        UUID orderId, UUID memberId, String orderNo, String buyerNickname, String reason,
        String itemsSummary)
        implements DomainEvent {

    /**
     * ⚠ <b>{@code order.requestReturn(...)} 뒤에 부른다.</b> {@code itemsSummary} 는 품목의
     * {@code returnRequestedQuantity} 에서 나오므로, 요청을 반영하기 전에 부르면 <b>빈 문자열</b>이다.
     */
    public static OrderReturnRequestedEvent from(Order order) {
        return new OrderReturnRequestedEvent(order.getId(), order.getMemberId(),
                order.getOrderNo(), order.getBuyerNickname(), order.getReturnReason(),
                order.requestedReturnSummary());
    }
}
