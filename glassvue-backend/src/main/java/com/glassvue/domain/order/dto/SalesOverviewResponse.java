package com.glassvue.domain.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 매출 대시보드 한 판 (2026-07-24, 백로그 C-11 · 2026-08-13 기간 선택 B-26).
 *
 * <p>요약·추이·상품별을 <b>한 응답</b>에 담는다. 화면이 대시보드 하나라 세 번 왕복할 이유가 없고,
 * 세 값이 <b>같은 시점</b>을 보고 있다는 것도 보장된다(따로 부르면 그 사이에 주문이 들어와 어긋난다).
 *
 * <p>🔴 <b>기간을 따르는 값과 안 따르는 값을 갈라 둔다</b>(B-26). {@code period}·{@code daily}·
 * {@code topProducts} 는 고른 기간을 보고, {@code today}·{@code allTime} 은 <b>기간과 무관</b>하다.
 * ⚠ 섞으면 *"지난달"* 을 골라 놓고 「오늘」 카드가 지난달 어느 날을 가리키게 된다 —
 * 화면에도 «기간과 무관» 이라고 적는다.
 *
 * <p>⚠ {@code from}·{@code to} 를 <b>응답에 되돌려 준다</b>: 화면이 보낸 값이 아니라 <b>서버가
 * 실제로 집계한 구간</b>이다(비우고 부르면 서버가 기본값을 정한다). 화면이 자기가 보낸 값으로
 * 제목을 쓰면 기본값일 때 «무엇을 보고 있는지» 를 못 적는다.
 *
 * @param from        집계 시작일 (KST, 포함)
 * @param to          집계 종료일 (KST, <b>포함</b>)
 * @param period      고른 기간의 요약
 * @param today       오늘 (KST 기준 00:00~) — <b>기간과 무관</b>
 * @param thisMonth   이번 달 (KST 기준 1일 00:00~) — <b>기간과 무관</b>.
 *                    ⚠ 매출 화면에는 안 그린다(프리셋 「이번 달」이 그 자리다). <b>관리자 홈</b>이 쓴다 —
 *                    B-26 에서 지우려다 {@code AdminDashboardView} 가 이 필드를 읽는 걸 발견했다.
 *                    지웠으면 **관리자 홈의 「이번 달」이 조용히 빈칸**이 됐다.
 * @param allTime     전체 기간 — <b>기간과 무관</b>
 * @param daily       기간의 일별 추이. <b>매출이 0인 날도 채워서</b> 준다
 * @param topProducts 기간 안에서 판매 수량 상위 상품
 */
public record SalesOverviewResponse(
        @Schema(description = "집계 시작일 (KST, 포함)", example = "2026-07-01") LocalDate from,
        @Schema(description = "집계 종료일 (KST, 포함)", example = "2026-07-15") LocalDate to,
        @Schema(description = "고른 기간 요약") SalesSummaryResponse period,
        @Schema(description = "오늘 (KST) — 기간과 무관") SalesSummaryResponse today,
        @Schema(description = "이번 달 (KST) — 기간과 무관. 관리자 홈이 쓴다") SalesSummaryResponse thisMonth,
        @Schema(description = "전체 기간 — 기간과 무관") SalesSummaryResponse allTime,
        @Schema(description = "기간의 일별 추이 (빈 날 포함)") List<DailySalesResponse> daily,
        @Schema(description = "기간의 판매 수량 TOP") List<ProductSalesResponse> topProducts
) {
}
