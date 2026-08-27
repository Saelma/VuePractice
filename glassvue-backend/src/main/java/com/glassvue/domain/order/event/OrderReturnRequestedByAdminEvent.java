package com.glassvue.domain.order.event;

import com.glassvue.domain.order.entity.Order;
import com.glassvue.global.messaging.DomainEvent;
import java.util.UUID;

/**
 * 관리자가 <b>대신</b> 건 반품 요청 (2026-08-27, BACKLOG §I-15).
 *
 * <p>🔴 <b>{@link OrderReturnRequestedEvent} 와 «받는 사람» 이 반대라 갈라 뒀다.</b>
 * 그쪽은 «고객이 요청했으니 관리자가 처리하라» 로 <b>관리자에게</b> 가는데, 이쪽은 관리자가 한
 * 일이라 관리자에게 알릴 것이 없다 — <b>모르고 있는 쪽은 고객</b>이다.
 * ⚠ 같은 이벤트에 «누가 했나» 플래그를 다는 길도 있었지만, 그러면 <b>구독자마다 «이번엔 내 차례인가»
 * 를 판단</b>해야 한다. 그 판단이 틀리면 알림이 두 번 뜨거나 아예 안 뜬다 — 부분 취소 알림이
 * {@code orderFullyCancelled} 한 칸에 걸려 있는 것이 이미 그 모양이고, 하나로 족하다.
 *
 * <p>⚠ <b>고객 본인이 요청한 경우에는 이 이벤트가 안 난다.</b> 자기가 한 일을 알림으로 되돌려
 * 받을 이유가 없다.
 *
 * <p>🔴 <b>{@code deadlinePassed} 를 싣는 이유</b> — 대행 요청은 <b>기한을 무시한다</b>(§I-15 결정 1).
 * 고객이 «반품 기간이 지났다» 는 화면을 보고 있다가 갑자기 반품이 접수되면 <b>화면과 알림이
 * 어긋나 보인다.</b> 그때 «관리자가 기간 경과 건을 접수했다» 고 말해 줘야 앞뒤가 맞는다.
 *
 * @param memberId       <b>알림 받을 사람</b>(주문자). ⚠ {@link OrderReturnRequestedEvent} 에서는
 *                       이 칸이 «요청한 사람» 이었다 — 이름이 같아도 뜻이 다르다.
 * @param adminName      대행한 관리자 닉네임 스냅샷. 고객이 «누가 했나» 를 물을 수 있어야 한다
 *                       (B-25 가 {@code cancelledByName} 을 남긴 것과 같은 판단).
 * @param reason         반품 사유. 대행은 사유가 <b>필수</b>라 비어 있지 않다.
 * @param deadlinePassed 기한을 넘긴 건이었나.
 */
public record OrderReturnRequestedByAdminEvent(
        UUID orderId, UUID memberId, String orderNo, String adminName,
        String reason, boolean deadlinePassed)
        implements DomainEvent {

    public static OrderReturnRequestedByAdminEvent of(Order order, String adminName,
                                                      boolean deadlinePassed) {
        return new OrderReturnRequestedByAdminEvent(order.getId(), order.getMemberId(),
                order.getOrderNo(), adminName, order.getReturnReason(), deadlinePassed);
    }
}
