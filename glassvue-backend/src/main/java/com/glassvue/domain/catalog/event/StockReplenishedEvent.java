package com.glassvue.domain.catalog.event;

import com.glassvue.global.messaging.DomainEvent;
import java.util.UUID;

/**
 * 상품의 <b>총재고가 0에서 양수로</b> 돌아왔을 때 발행되는 도메인 이벤트(재입고, B-9).
 *
 * <p>{@link StockRunningLowEvent} 와 대칭이다 — 재고는 catalog 가 소유하므로 발행 주체도 catalog 다.
 * 주문 취소·반품 복원(order 경로)이든 관리자 재고 편집(catalog 경로)이든 <b>같은 이벤트</b>가 나가야
 * 하므로 여기서 낸다. 구독자(재입고 신청자에게 알림)는 restock 도메인이 반응한다 — order·catalog 는 그 존재를 모른다.
 *
 * <p>단위가 <b>옵션이 아니라 상품</b>인 이유: 관리자 상품 편집이 옵션을 delete + 재삽입하며 variant.id 가
 * 매번 새로 생겨(관리자 재고 편집 경로 참조) 옵션 id 로는 구독을 안정적으로 이을 수 없다. 그래서 구독·이벤트
 * 모두 상품 단위다(멀티옵션 상품은 어느 옵션이 들어와도 재입고로 본다 — 위시리스트와 같은 상품 단위 감각).
 */
public record StockReplenishedEvent(UUID productId, String productName) implements DomainEvent {
}
