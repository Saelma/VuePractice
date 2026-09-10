package com.glassvue.domain.catalog.event;

import com.glassvue.global.messaging.DomainEvent;
import java.util.UUID;

/**
 * 상품이 <b>영구 삭제</b>됐을 때 발행되는 도메인 이벤트 (F-7 purge, 2026-09-10 · BACKLOG M-4).
 *
 * <p>⚠ <b>소프트 삭제(휴지통)에서는 안 나간다.</b> 그건 되돌릴 수 있고
 * ({@code PRODUCT_RESTORE} 가 실제로 2건 있다) 되돌린 뒤에도 알림이 살아 있어야 한다.
 * 🔴 <b>purge 는 «되돌릴 수 없는 구간»</b> 이고, 딸린 것을 치우는 자리도 거기다.
 *
 * <p><b>왜 이벤트인가</b>: 알림은 notification 도메인이 갖고 catalog 는 그 존재를 몰라야 한다
 * (CLAUDE.md — 도메인 간 직접 참조 금지). {@code StockRunningLowEvent} 와 같은 모양이다.
 *
 * <p>⚠ <b>이 이벤트가 상품 purge 로 다른 도메인을 정리하는 첫 경로다</b> — 2026-09-10 이전에는
 * 아무것도 안 치웠고, 그래서 없는 상품을 가리키는 알림이 <b>55건</b> 쌓였다.
 *
 * @param productId   사라진 상품 id — 구독자는 이것으로 자기 쪽 흔적을 찾는다
 * @param productName 로그·감사에 남길 이름. 🔴 <b>상품 행이 이미 없으므로 나중에 조회할 수 없다</b>
 */
public record ProductPurgedEvent(UUID productId, String productName) implements DomainEvent {
}
