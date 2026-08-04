package com.glassvue.domain.catalog.dto;

import com.glassvue.domain.catalog.entity.StockChangeReason;
import com.glassvue.domain.catalog.entity.StockHistory;
import java.time.Instant;
import java.util.UUID;

/**
 * 재고 변동 한 줄(관리자 화면용).
 *
 * @param variantName 옵션명 스냅샷 — 지금 옵션 목록에 없는 이름이 나올 수 있다(삭제된 옵션의 과거 이력)
 * @param quantity    부호 있는 변동량(차감 −, 복원·입고 +)
 * @param stockAfter  변동 직후 재고
 * @param orderId     주문 경로면 그 주문. 관리자 조작은 null
 * @param actorName   관리자 조작이면 행위자 닉네임 스냅샷. 주문 경로는 null
 */
public record StockHistoryResponse(
        UUID id,
        String variantName,
        StockChangeReason reason,
        long quantity,
        long stockAfter,
        UUID orderId,
        String actorName,
        Instant createdAt
) {
    public static StockHistoryResponse from(StockHistory h) {
        return new StockHistoryResponse(
                h.getId(),
                h.getVariantName(),
                h.getReason(),
                h.getQuantity(),
                h.getStockAfter(),
                h.getOrderId(),
                h.getActorName(),
                h.getCreatedAt());
    }
}
