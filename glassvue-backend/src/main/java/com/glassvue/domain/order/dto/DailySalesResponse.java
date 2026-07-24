package com.glassvue.domain.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 일별 매출 한 줄. 날짜는 <b>KST 기준</b>이다 — {@code paid_at} 은 UTC 로 저장돼 있어서
 * 변환 없이 자르면 한국 시간 00:00~09:00 결제가 전날로 찍힌다.
 *
 * <p>매출이 0인 날도 <b>행이 있다</b>(서비스가 채운다). 없으면 차트에 구멍이 생기고,
 * "그날 매출이 0이었다"와 "그날이 아예 없다"를 화면이 구분할 수 없다.
 *
 * @param date 표시용 {@code yyyy-MM-dd} 문자열. 화면이 그대로 축에 쓴다
 */
public record DailySalesResponse(
        @Schema(description = "날짜 (KST)", example = "2026-07-24") String date,
        @Schema(description = "그날 매출로 잡힌 주문 수", example = "2") long orderCount,
        @Schema(description = "상품매출", example = "20000") long itemSales,
        @Schema(description = "배송비 수입", example = "0") long shippingSales
) {

    public static DailySalesResponse empty(String date) {
        return new DailySalesResponse(date, 0, 0, 0);
    }
}
