package com.glassvue.domain.cart.dto;

import com.glassvue.domain.catalog.entity.ProductStatus;
import java.util.UUID;

public record CartItemResponse(
        UUID productId,
        String name,
        long price,
        // 정가(할인 전). 주문 생성 시 이 값을 스냅샷하므로 장바구니 응답에도 실어야 한다.
        Long listPrice,
        ProductStatus status,
        long quantity,
        long lineTotal,
        boolean available, // 판매중 + 재고 충분
        String thumbUrl // 대표 이미지 썸네일(없으면 null). 주문 생성 시 이 값을 스냅샷한다
) {
}
