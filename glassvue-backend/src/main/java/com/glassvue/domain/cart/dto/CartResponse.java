package com.glassvue.domain.cart.dto;

import java.util.List;

/**
 * 장바구니 응답.
 *
 * <p>{@code totalPrice}는 <b>상품 합계</b>(배송비 제외)다. 배송비는 주문 전 미리보기로 함께 내려
 * 주문서에서 "상품 합계 / 배송비 / 결제 금액"을 그대로 그릴 수 있게 한다 —
 * 화면이 배송비 정책(금액·무료 기준)을 알 필요가 없다.
 *
 * <p>{@code amountUntilFree}는 무료배송까지 남은 금액. 0이면 이미 무료이거나 무료배송 정책이 없다.
 */
public record CartResponse(
        List<CartItemResponse> items,
        long totalQuantity,
        long totalPrice,
        long shippingFee,
        long payAmount,
        long amountUntilFree
) {
}
