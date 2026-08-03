package com.glassvue.domain.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 관리자 대시보드의 「재고 부족」 한 판 (2026-08-03, 백로그 B-16).
 *
 * <p><b>{@code threshold} 를 같이 내려주는 게 이 응답의 요점이다.</b> 기준값은 서버 설정
 * ({@code catalog.low-stock-threshold})에 있는데, 화면이 그걸 모르면 <i>"5개 이하"</i> 같은 문구를
 * 프론트에 또 적어야 하고 <b>설정을 바꾸면 화면이 거짓말을 시작한다</b>. 가입 쿠폰(G-2)에서
 * "혜택 문구는 서버가 값을 줄 때만 노출한다"고 정한 것과 같은 자리다.
 *
 * <p>같은 이유로 {@code count} 와 {@code items} 를 나눈다 — 목록은 상위 몇 줄이고 숫자는 전체다.
 *
 * @param threshold 이 값 <b>이하</b>면 부족으로 본다. 재고 부족 알림({@code StockRunningLowEvent})과 같은 기준
 * @param count     조건에 걸린 옵션 <b>전체</b> 건수
 * @param items     재고 적은 순 상위 몇 줄. {@code count} 보다 짧을 수 있다
 */
public record LowStockResponse(
        @Schema(description = "부족 판정 기준 (이 값 이하)") long threshold,
        @Schema(description = "부족한 옵션 전체 건수") long count,
        @Schema(description = "재고 적은 순 상위 목록") List<LowStockItemResponse> items
) {
}
