package com.glassvue.domain.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * 상품별 판매 실적 한 줄.
 *
 * <p>상품명은 <b>주문 시점 스냅샷</b>이고, 이름이 바뀐 상품은 가장 최근 결제 건의 이름으로 보여준다
 * (묶는 기준은 {@code productId}). catalog 를 조회하지 않으므로 도메인 의존이 생기지 않는다.
 *
 * <p>⚠ {@code sales} 는 <b>쿠폰 할인 전</b>이다. 쿠폰은 주문 단위로 붙어서 어느 상품이 얼마를 깎았는지
 * 나눌 근거가 없다 — 안분하면 그럴듯하지만 지어낸 숫자가 된다.
 * 그래서 이 값들의 합계는 요약의 {@code itemSales}(할인 후)와 <b>일부러 다르다.</b>
 */
public record ProductSalesResponse(
        @Schema(description = "상품 id") UUID productId,
        @Schema(description = "상품명 (주문 시점 스냅샷)", example = "몽쉘") String productName,
        @Schema(description = "판매 수량", example = "12") long quantity,
        @Schema(description = "판매액 (쿠폰 할인 전)", example = "120000") long sales
) {
}
