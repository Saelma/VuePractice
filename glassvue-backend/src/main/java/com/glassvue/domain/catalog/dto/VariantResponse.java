package com.glassvue.domain.catalog.dto;

import com.glassvue.domain.catalog.entity.ProductDiscount;
import com.glassvue.domain.catalog.entity.ProductVariant;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * 상품 옵션 하나 (2026-07-24, C-8).
 *
 * <p>화면이 옵션별 재고·가격·품절을 그대로 그릴 수 있게 담는다. {@code price} 는 이미 기본가+가격차가
 * 반영된 <b>실제 판매가</b>라 화면이 계산할 필요가 없다(delta 는 참고용으로 함께 준다).
 *
 * <p>🔴 <b>{@code price} 에는 기간 할인까지 반영된다</b>(2026-08-19, G-5). 이 값이
 * <b>장바구니·주문이 그대로 복사해 가는 금액</b>이라(CartService → OrderService) 여기에 세일이
 * 안 실리면 «화면엔 세일가, 결제는 원가» 가 된다. 세일 전 값은 {@code regularPrice} 로 함께 준다.
 */
public record VariantResponse(
        UUID id,
        String name,
        @Schema(description = "기본가 대비 가격차", example = "2000") long priceDelta,
        @Schema(description = "실제 판매가 = ROUND((기본가 + 가격차) × (100 - 할인율) / 100)", example = "26560")
        long price,
        @Schema(description = "세일 전 판매가 = 기본가 + 가격차. 세일 중이 아니면 price 와 같다", example = "33200")
        long regularPrice,
        long stock,
        boolean soldOut
) {
    /**
     * @param basePrice 상품 <b>기본가</b>(세일이 반영되지 않은 값)
     * @param discount  지금 유효한 할인. 없으면 {@code null}
     *
     *                  <p>🔴 <b>순서가 중요하다</b>: 가격차를 <b>더한 뒤에</b> 할인율을 먹인다.
     *                  반대로 하면 "L +2000" 옵션만 할인이 덜 먹어 <b>옵션마다 체감 할인율이 달라진다.</b>
     *                  ⚠ 그래서 {@code basePrice} 에 이미 할인된 값을 넘기면 안 된다 — 그러면
     *                  가격차가 할인 뒤에 붙어 같은 고장이 난다.
     */
    public static VariantResponse from(ProductVariant v, long basePrice, ProductDiscount discount) {
        long regular = v.effectivePrice(basePrice);
        long price = discount == null ? regular : discount.applyTo(regular);
        return new VariantResponse(v.getId(), v.getName(), v.getPriceDelta(),
                price, regular, v.getStock(), v.isSoldOut());
    }
}
