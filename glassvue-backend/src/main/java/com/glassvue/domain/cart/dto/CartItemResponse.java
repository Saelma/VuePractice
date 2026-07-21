package com.glassvue.domain.cart.dto;

import com.glassvue.domain.catalog.entity.ProductStatus;
import java.util.UUID;

public record CartItemResponse(
        UUID productId,
        String name,
        long price,
        ProductStatus status,
        long quantity,
        long lineTotal,
        boolean available, // 판매중 + 재고 충분
        String thumbUrl // 대표 이미지 썸네일(없으면 null). 주문 생성 시 이 값을 스냅샷한다
) {
}
