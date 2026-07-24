package com.glassvue.domain.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 한 기간의 매출 요약.
 *
 * <p><b>상품매출과 배송비를 합치지 않는다</b>(사용자 결정, 2026-07-24). 배송비는 그대로 택배비로
 * 나가는 돈이라 상품매출에 섞으면 "장사가 잘되고 있나"를 읽을 수 없다.
 * 둘을 더한 값이 필요하면 {@code payAmount} 를 본다.
 *
 * @param orderCount     매출로 잡힌 주문 수 (PAID·SHIPPED·DELIVERED)
 * @param itemSales      상품매출 = 상품합계 − 쿠폰할인
 * @param shippingSales  배송비 수입
 * @param couponDiscount 쿠폰으로 깎아준 금액 (얼마나 태웠는지 보려고 따로 낸다)
 * @param payAmount      실제 결제된 총액 = itemSales + shippingSales
 * @param averageOrderAmount 주문당 평균 결제금액. 주문이 0건이면 0
 */
public record SalesSummaryResponse(
        @Schema(description = "매출로 잡힌 주문 수", example = "6") long orderCount,
        @Schema(description = "상품매출 (상품합계 − 쿠폰할인)", example = "60000") long itemSales,
        @Schema(description = "배송비 수입", example = "3000") long shippingSales,
        @Schema(description = "쿠폰으로 깎아준 금액", example = "5000") long couponDiscount,
        @Schema(description = "실제 결제된 총액", example = "63000") long payAmount,
        @Schema(description = "주문당 평균 결제금액", example = "10500") long averageOrderAmount
) {

    public static SalesSummaryResponse of(long orderCount, long itemSales,
                                          long shippingSales, long couponDiscount) {
        long pay = itemSales + shippingSales;
        // 0건일 때 나눗셈을 하지 않는다. 평균은 "주문이 없으면 0"이 사실에 가깝다.
        long average = orderCount == 0 ? 0 : pay / orderCount;
        return new SalesSummaryResponse(orderCount, itemSales, shippingSales, couponDiscount, pay, average);
    }

    public static SalesSummaryResponse empty() {
        return new SalesSummaryResponse(0, 0, 0, 0, 0, 0);
    }
}
