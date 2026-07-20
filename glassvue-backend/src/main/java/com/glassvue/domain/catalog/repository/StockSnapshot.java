package com.glassvue.domain.catalog.repository;

/**
 * 재고 차감 직후의 상품 이름·잔여재고 스냅샷.
 *
 * 엔티티가 아닌 **스칼라 프로젝션**인 게 핵심 — {@code decreaseStock}은 벌크 JPQL UPDATE라
 * 1차 캐시에 올라와 있는 Product 엔티티를 갱신하지 않는다. 같은 트랜잭션에서 findById로 읽으면
 * 차감 전 재고(stale)가 나오므로, DB를 직접 읽는 프로젝션으로 조회한다.
 */
public record StockSnapshot(String name, long stock) {
}
