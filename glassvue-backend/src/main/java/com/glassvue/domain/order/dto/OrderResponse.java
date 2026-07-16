package com.glassvue.domain.order.dto;

import com.glassvue.domain.order.entity.Order;
import com.glassvue.domain.order.entity.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        OrderStatus status,
        long totalPrice,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant paidAt,
        Instant shippedAt
) {
    public static OrderResponse from(Order o) {
        return new OrderResponse(
                o.getId(),
                o.getStatus(),
                o.getTotalPrice(),
                o.getItems().stream().map(OrderItemResponse::from).toList(),
                o.getCreatedAt(),
                o.getPaidAt(),
                o.getShippedAt());
    }
}
