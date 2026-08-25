package com.glassvue.domain.order.event;

import com.glassvue.global.messaging.DomainEvent;
import java.util.List;
import java.util.UUID;

/**
 * 부분 취소 도메인 이벤트 (2026-08-25, BACKLOG G-10 착수 중 발견한 G-4 구멍).
 *
 * <p>🔴 <b>왜 {@code OrderCancelledEvent} 를 재사용하지 않나</b> — 그 이벤트의 구독자는 <b>둘</b>이다:
 * catalog(판매량)와 notification(«주문이 취소되었습니다»). 부분 취소에 그걸 그대로 내면
 * <b>1개만 뺐는데 «주문이 취소되었다» 고 알린다.</b> 지금 필요한 것은 판매량 되돌림 하나뿐이라
 * 이벤트를 나눴다.
 *
 * <p>⚠ <b>왜 이 이벤트가 없으면 안 되나</b> — 부분 취소는 {@code OrderCancelledEvent} 를
 * <b>전량이 빠질 때만</b> 낸다. 즉 «부분 취소하고 그대로 두는» 정상 경로에서는 아무 이벤트도 안 나가고,
 * {@code product.sold_count} 가 <b>취소된 만큼 안 줄어든다.</b> 인기순이 틀어진다.
 * 🔴 2026-08-25 실측 시점에 어긋난 값은 없었다 — 08-24 검증의 세 주문이 전부 {@code CANCELLED} 로
 * 끝나 «+전량 / −전량» 이 상쇄됐기 때문이다. <b>즉 우연히 맞았다</b>(08-24 §5-4 매출 집계와 같은 모양).
 *
 * <p>⚠ <b>알림은 아직 여기 안 붙는다.</b> 관리자가 대신 부분 취소하면 고객에게 아무 말이 없는데,
 * 그건 이 이벤트가 만든 문제가 아니라 G-4 가 두고 간 자리다(전체 취소는 알림이 간다).
 * 붙이려면 «무엇을 몇 개 뺐고 얼마를 돌려줬나» 를 이벤트에 실어야 하므로 <b>별도 항목</b>으로 둔다.
 *
 * @param lines 이번 회차에 <b>빠진 만큼만</b> — 원본 수량이 아니다(WA §1-2-1)
 */
public record OrderItemCancelledEvent(UUID orderId, UUID memberId, List<SoldLine> lines)
        implements DomainEvent {
}
