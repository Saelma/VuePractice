package com.glassvue.domain.cart.dto;

import com.glassvue.domain.catalog.entity.ProductStatus;
import java.util.UUID;

/**
 * 장바구니 한 줄 (2026-07-24 C-8: 옵션 단위).
 *
 * <p>담기는 단위가 옵션(variant)이 됐다. {@code productId} 는 상품 페이지 링크·주문 스냅샷용으로 유지하고,
 * {@code variantId}·{@code optionName} 을 더했다. {@code price} 는 옵션 가격차가 반영된 실제 판매가다.
 *
 * @param optionName 옵션명. 단일 옵션 상품이면 {@code null}(화면에 "기본" 노이즈를 안 남긴다)
 */
public record CartItemResponse(
        UUID productId,
        UUID variantId,
        String name,
        String optionName,
        long price,
        Long listPrice,
        ProductStatus status,
        long quantity,
        long lineTotal,
        boolean available,
        String thumbUrl
) {
}
