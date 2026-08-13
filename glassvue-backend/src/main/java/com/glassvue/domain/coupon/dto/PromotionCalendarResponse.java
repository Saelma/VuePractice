package com.glassvue.domain.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 프로모션 달력 한 달치(B-27, 관리자 전용).
 *
 * <p>🔴 <b>목록으로는 안 보이고 달력으로만 보이는 질문 하나</b>에 답하는 화면이다 —
 * *"이 날 무엇이 <b>동시에</b> 돌고 있나"*. 할인이 겹치면 마진이 겹쳐서 깎인다.
 *
 * <p>⚠ 화면이 달력 격자를 그리려면 «그 달 1일이 무슨 요일인가» 를 알아야 하는데,
 * 그건 화면이 계산해도 시간대에 안 흔들린다(순수 달력 산수). <b>흔들리는 것은 막대의 날짜</b>라
 * 그쪽만 서버가 잘라 준다({@link PromotionSpanResponse}).
 */
public record PromotionCalendarResponse(
        @Schema(description = "조회한 달 (KST)", example = "2026-08") String month,
        @Schema(description = "그 달의 일수", example = "31") int daysInMonth,
        @Schema(description = "그 달 1일의 요일 (1=월 … 7=일)", example = "6") int firstDayOfWeek,
        List<PromotionSpanResponse> spans
) {
}
