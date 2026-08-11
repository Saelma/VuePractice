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
 * <p>🔴 <b>2026-08-11 같은 날 고쳤다 — 처음엔 사유가 없었다.</b> 그때 문구는 «주문 상세에서
 * 확인해 주세요» 였는데 <b>그 상세에 아무것도 없었다</b>(카드가 {@code DELIVERED} 를 렌더 조건에서
 * 빼 놓았고 거절 기록도 안 남았다). 사용자가 브라우저 검증 중에 *"뭘 확인하라는 지 안 나와있어"* 로
 * 지적해 드러났다.
 * ⚠ 교훈: <b>«없는 값을 지어내지 않는다» 와 «없는 곳을 가리키지 않는다» 는 다른 규칙이다.</b>
 * 앞은 지켰는데 뒤를 어겼다 — 값이 없으면 문장을 비우는 게 아니라 <b>값을 만들어야</b> 했다(V47).
 */
public record OrderReturnRejectedEvent(UUID orderId, UUID memberId, String reason) implements DomainEvent {

    public static OrderReturnRejectedEvent from(Order order) {
        return new OrderReturnRejectedEvent(order.getId(), order.getMemberId(),
                order.getReturnRejectedReason());
    }
}
