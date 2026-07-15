package com.glassvue.domain.cart.dto;

import java.util.List;

public record CartResponse(
        List<CartItemResponse> items,
        long totalQuantity,
        long totalPrice
) {
}
