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
 * <p>🔴 <b>고객 알림이 여기 붙는다</b> (2026-08-25, §I). 처음엔 판매량 되돌림만 싣고 «알림은 별도 항목»
 * 으로 뒀는데, <b>그러면 관리자가 대신 부분 취소해도 고객에게 아무 말이 없다</b> — 전체 취소는
 * 알림이 가므로 그게 비대칭이었다(2026-08-11 이 반품에 대해 고친 것의 거울상).
 * 그래서 «무엇을 몇 개 뺐고 얼마를 돌려줬나» 를 함께 싣는다.
 *
 * <p>⚠ <b>핸들러는 주문 엔티티를 못 본다</b>(도메인 경계) — {@code OrderReturnedEvent} 가
 * {@code refundedPoint} 를 싣는 것과 <b>같은 자리·같은 이유</b>다.
 *
 * @param lines          이번 회차에 <b>빠진 만큼만</b> — 원본 수량이 아니다(WA §1-2-1)
 * @param itemsSummary   «지바 1개» 처럼 사람이 읽는 요약. 감사 원장이 쓰는 것과 같은 문자열이다
 * @param refundedAmount 이번 회차에 돌려줄 <b>돈</b> (취소는 «반품금액 − 쿠폰몫 − 적립금몫»)
 * @param refundedPoint  이번 회차에 계정으로 되돌린 <b>적립금</b>
 * @param orderFullyCancelled 🔴 이번 취소로 <b>주문에 남은 것이 없어졌나</b>. 그때는 뒤이어
 *                       {@code OrderCancelledEvent} 가 따로 나가므로 <b>알림을 두 번 보내지 않는다</b>
 *                       («주문 일부가 취소되었어요» + «주문이 취소되었어요» 가 연달아 뜬다).
 *                       ⚠ <b>이벤트 자체를 막을 수는 없다</b> — 판매량 되돌림은 이 회차 몫이
 *                       여기에만 실려 있어서다. <b>«무엇을 하느냐» 를 구독자마다 가른다.</b>
 */
public record OrderItemCancelledEvent(UUID orderId, UUID memberId, List<SoldLine> lines,
                                      String itemsSummary, long refundedAmount, long refundedPoint,
                                      boolean orderFullyCancelled)
        implements DomainEvent {
}
