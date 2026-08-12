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
 * <p>⚠ <b>{@code lines} 가 없다.</b> 요청 단계에서는 재고도 적립금도 <b>안 움직인다</b>
 * (승인 때 움직인다 — {@code OrderService.approveReturn}). 구독자는 알림 하나뿐이다.
 * 없는 필드를 «짝이니까» 로 넣지 않는다({@link OrderReturnRejectedEvent} 와 같은 판단).
 *
 * <p>⚠ <b>{@code memberId} 는 「요청한 사람」이지 「알림 받을 사람」이 아니다.</b> 앞의 둘에서는
 * 그 둘이 같았는데 여기서는 다르다 — 받는 쪽은 관리자 전원이라 <b>이벤트가 대상을 모른다.</b>
 * 대상 선정은 {@code MemberService.adminIds()} 가 하고 이벤트는 «무슨 일이 있었나» 만 싣는다.
 *
 * @param orderNo        사람이 읽는 주문번호 — 알림 문구가 «어느 주문인지» 를 말해야 관리자가 고른다.
 * @param buyerNickname  요청한 사람. ⚠ <b>스냅샷</b>이라 나중에 닉네임이 바뀌어도 알림은 그대로다
 *                       (알림은 «그때 무슨 일이 있었나» 의 기록이다).
 * @param reason         반품 사유. 선택값이라 <b>비어 있을 수 있다</b> — 문구에서 갈라 준다.
 */
public record OrderReturnRequestedEvent(
        UUID orderId, UUID memberId, String orderNo, String buyerNickname, String reason)
        implements DomainEvent {

    public static OrderReturnRequestedEvent from(Order order) {
        return new OrderReturnRequestedEvent(order.getId(), order.getMemberId(),
                order.getOrderNo(), order.getBuyerNickname(), order.getReturnReason());
    }
}
