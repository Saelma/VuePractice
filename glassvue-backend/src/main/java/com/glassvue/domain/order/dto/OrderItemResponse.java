package com.glassvue.domain.order.dto;

import com.glassvue.domain.order.entity.OrderItem;
import java.util.UUID;

/** {@code productImageUrl}은 주문 시점 썸네일 스냅샷 — 상품이 바뀌거나 삭제돼도 그때 모습을 보여준다. */
public record OrderItemResponse(
        UUID productId,
        String productName,
        // 옵션명 스냅샷. 단일 옵션/옵션 이전 주문이면 null(화면이 옵션 줄을 감춘다).
        String optionName,
        String productImageUrl,
        long price,
        // 주문 시점 정가 스냅샷(V16). null이면 할인 없이 샀거나 정가 도입 이전 주문이다.
        Long listPrice,
        long quantity,
        long lineTotal
) {
    public static OrderItemResponse from(OrderItem i) {
        return new OrderItemResponse(i.getProductId(), i.getProductName(), i.getVariantName(), i.getProductImageUrl(),
                i.getPrice(), i.getListPrice(), i.getQuantity(), i.getLineTotal());
    }
}
