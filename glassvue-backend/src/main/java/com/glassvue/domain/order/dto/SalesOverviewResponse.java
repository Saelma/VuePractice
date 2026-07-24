package com.glassvue.domain.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 관리자 매출 대시보드 한 판 (2026-07-24, 백로그 C-11).
 *
 * <p>요약·추이·상품별을 <b>한 응답</b>에 담는다. 화면이 대시보드 하나라 세 번 왕복할 이유가 없고,
 * 세 값이 <b>같은 시점</b>을 보고 있다는 것도 보장된다(따로 부르면 그 사이에 주문이 들어와 어긋난다).
 *
 * @param today     오늘 (KST 기준 00:00~)
 * @param thisMonth 이번 달 (KST 기준 1일 00:00~)
 * @param allTime   전체 기간
 * @param daily     최근 N일 일별 추이. <b>매출이 0인 날도 채워서</b> 준다
 * @param topProducts 판매 수량 상위 상품
 */
public record SalesOverviewResponse(
        @Schema(description = "오늘 (KST)") SalesSummaryResponse today,
        @Schema(description = "이번 달 (KST)") SalesSummaryResponse thisMonth,
        @Schema(description = "전체 기간") SalesSummaryResponse allTime,
        @Schema(description = "일별 추이 (빈 날 포함)") List<DailySalesResponse> daily,
        @Schema(description = "판매 수량 TOP") List<ProductSalesResponse> topProducts
) {
}
