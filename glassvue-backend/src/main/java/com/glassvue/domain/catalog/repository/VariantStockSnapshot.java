package com.glassvue.domain.catalog.repository;

import java.util.UUID;

/**
 * 옵션 재고 차감 직후의 스냅샷(상품명·옵션명·잔여재고) — 재고 부족 알림 판단용.
 * 스칼라 프로젝션인 이유는 벌크 UPDATE 뒤 1차 캐시 stale 을 피하려는 것이다({@link StockSnapshot} 과 같은 이유).
 */
public record VariantStockSnapshot(UUID productId, String productName, String variantName, long stock) {
}
