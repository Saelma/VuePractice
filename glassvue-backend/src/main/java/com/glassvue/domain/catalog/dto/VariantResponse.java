package com.glassvue.domain.catalog.dto;

import com.glassvue.domain.catalog.entity.ProductVariant;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * 상품 옵션 하나 (2026-07-24, C-8).
 *
 * <p>화면이 옵션별 재고·가격·품절을 그대로 그릴 수 있게 담는다. {@code price} 는 이미 기본가+가격차가
 * 반영된 <b>실제 판매가</b>라 화면이 계산할 필요가 없다(delta 는 참고용으로 함께 준다).
 */
public record VariantResponse(
        UUID id,
        String name,
        @Schema(description = "기본가 대비 가격차", example = "2000") long priceDelta,
        @Schema(description = "실제 판매가 = 기본가 + 가격차", example = "33200") long price,
        long stock,
        boolean soldOut
) {
    public static VariantResponse from(ProductVariant v, long basePrice) {
        return new VariantResponse(v.getId(), v.getName(), v.getPriceDelta(),
                v.effectivePrice(basePrice), v.getStock(), v.isSoldOut());
    }
}
