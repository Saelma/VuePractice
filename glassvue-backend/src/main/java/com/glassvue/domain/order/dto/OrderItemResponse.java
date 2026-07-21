package com.glassvue.domain.order.dto;

import com.glassvue.domain.order.entity.OrderItem;
import java.util.UUID;

/** {@code productImageUrl}은 주문 시점 썸네일 스냅샷 — 상품이 바뀌거나 삭제돼도 그때 모습을 보여준다. */
public record OrderItemResponse(
        UUID productId,
        String productName,
        String productImageUrl,
        long price,
        long quantity,
        long lineTotal
) {
    public static OrderItemResponse from(OrderItem i) {
        return new OrderItemResponse(i.getProductId(), i.getProductName(), i.getProductImageUrl(),
                i.getPrice(), i.getQuantity(), i.getLineTotal());
    }
}
