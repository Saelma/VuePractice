package com.glassvue.domain.catalog.dto;

import com.glassvue.domain.catalog.repository.LowStockVariant;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * 재고 부족 옵션 한 줄 (2026-08-03, B-16).
 *
 * <p>{@code productId} 를 함께 주는 이유는 화면이 <b>상품 상세로 갈 수 있어야</b> 하기 때문이다 —
 * 대시보드가 "부족하다"만 말하고 고치러 갈 길을 안 주면 결국 관리자가 상품을 다시 찾아야 한다.
 */
public record LowStockItemResponse(
        @Schema(description = "상품 ID (상세로 이동)") UUID productId,
        @Schema(description = "상품명") String productName,
        @Schema(description = "옵션명") String variantName,
        @Schema(description = "남은 재고") long stock
) {
    public static LowStockItemResponse from(LowStockVariant v) {
        return new LowStockItemResponse(v.productId(), v.productName(), v.variantName(), v.stock());
    }
}
