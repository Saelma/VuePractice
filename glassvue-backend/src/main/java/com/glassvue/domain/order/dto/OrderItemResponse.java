package com.glassvue.domain.order.dto;

import com.glassvue.domain.order.entity.OrderItem;
import java.util.UUID;

/** {@code productImageUrl}은 주문 시점 썸네일 스냅샷 — 상품이 바뀌거나 삭제돼도 그때 모습을 보여준다. */
public record OrderItemResponse(
        UUID productId,
        // 옵션(variant) id — "다시 담기"(재구매)가 장바구니에 담을 때 쓴다(장바구니는 옵션 단위, B-8).
        // 스냅샷이라 그 사이 옵션이 삭제됐을 수 있다 → 담기 실패는 화면이 안내한다.
        UUID variantId,
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
        return new OrderItemResponse(i.getProductId(), i.getVariantId(), i.getProductName(), i.getVariantName(),
                i.getProductImageUrl(), i.getPrice(), i.getListPrice(), i.getQuantity(), i.getLineTotal());
    }
}
