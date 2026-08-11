package com.glassvue.domain.order.event;

import com.glassvue.domain.order.entity.Order;
import com.glassvue.global.messaging.DomainEvent;
import java.util.UUID;

/**
 * 반품 <b>거절</b> 도메인 이벤트 (2026-08-11, 08-10 §16-4 4번).
 *
 * <p>🔴 <b>이 자리가 셋 중 가장 나빴다.</b> 승인은 그래도 적립금이 들어와 고객이 «뭔가 됐구나» 를
 * 알 수 있지만, 거절은 <b>상태가 조용히 {@code DELIVERED} 로 되돌아갈 뿐</b>이라
 * <b>요청해 놓고 영영 소식이 없다.</b> 고객이 주문 상세를 다시 열어 보기 전까지 모른다
 * (문의 답변 알림 B-15 를 만든 이유와 정확히 같은 구조 — «읽기 전용 종착점이 아니다»).
 *
 * <p>⚠ <b>{@code lines} 가 없다.</b> 승인과 달리 거절은 재고도 적립금도 <b>안 건드리므로</b>
 * catalog 가 되돌릴 것이 없다 — 구독자는 알림 하나뿐이다. 없는 필드를 «짝이니까» 로 넣지 않는다
 * (넣으면 다음 사람이 «판매량을 되돌리나?» 로 읽는다).
 *
 * <p>⚠ 거절 <b>사유</b>는 아직 없다. {@code Order.rejectReturn()} 이 사유를 받지 않기 때문이고,
 * 그건 별개 작업이다(취소 사유가 B-17 에서 뒤늦게 붙은 것과 같은 자리). 문구는 «주문 상세에서
 * 확인해 주세요» 로 링크를 준다 — <b>없는 값을 지어내 문장에 넣지 않는다.</b>
 */
public record OrderReturnRejectedEvent(UUID orderId, UUID memberId) implements DomainEvent {

    public static OrderReturnRejectedEvent from(Order order) {
        return new OrderReturnRejectedEvent(order.getId(), order.getMemberId());
    }
}
