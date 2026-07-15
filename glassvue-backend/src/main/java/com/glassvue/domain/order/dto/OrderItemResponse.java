package com.glassvue.domain.order.dto;

import com.glassvue.domain.order.entity.OrderItem;
import java.util.UUID;

public record OrderItemResponse(
        UUID productId,
        String productName,
        long price,
        long quantity,
        long lineTotal
) {
    public static OrderItemResponse from(OrderItem i) {
        return new OrderItemResponse(i.getProductId(), i.getProductName(), i.getPrice(), i.getQuantity(), i.getLineTotal());
    }
}
