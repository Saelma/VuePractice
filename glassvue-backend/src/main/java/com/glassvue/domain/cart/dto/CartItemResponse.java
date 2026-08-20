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
        /**
         * 🔴 <b>세일 전 판매가</b>(기본가 + 옵션 가격차) — 2026-08-20, BACKLOG G-9.
         *
         * <p>세일 중이 아니면 {@code price} 와 같다. <b>주문이 이 값을 스냅샷한다</b> —
         * 그전에는 통로가 아예 없어서 «원래 얼마였나» 가 주문에 안 남았다.
         *
         * <p>⚠ {@code listPrice}(정가)와 <b>다른 값이다.</b> 정가는 관리자가 손으로 넣는
         * «원래 이 값어치» 라 <b>비어 있을 수 있고</b>, 이건 서버가 계산하는
         * «이 세일이 없었으면 받았을 금액» 이라 <b>항상 있다.</b>
         */
        long regularPrice,
        Long listPrice,
        ProductStatus status,
        long quantity,
        long lineTotal,
        boolean available,
        String thumbUrl
) {
}
