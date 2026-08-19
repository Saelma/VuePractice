package com.glassvue.domain.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 기간 할인 등록·수정 요청 (2026-08-19, BACKLOG G-5).
 *
 * <p>🔴 <b>{@code Instant} 가 아니라 {@code LocalDate} 를 받는다</b> — B-26(관리자 기간 선택)에서 세운
 * 규약 그대로다. 화면이 시각을 만들어 보내면 <b>KST 경계가 두 곳에서 계산되고, 하루가 어긋나도
 * 화면은 멀쩡해 보인다.</b> 경계를 만드는 곳이 서버 한 곳이어야 그 갈래가 아예 없다.
 *
 * <p>⚠ <b>{@code endDate} 는 포함이다.</b> "8/22~8/24" 면 24일이 <b>끝나는 순간까지</b> 세일이다.
 * 배타 경계({@code 25일 00:00})로 바꾸는 것은 서비스가 하고, 그래서 관리자는 «하루 빼서 적는» 일을
 * 하지 않는다. {@code 23:59:59} 로 잡지 않는 이유도 B-26 과 같다 — 그 사이 {@code 23:59:59.5} 결제가
 * 원가로 나가는데 <b>초 미만은 눈에 안 보여 더 나쁘다.</b>
 */
public record ProductDiscountRequest(

        @Schema(description = "할인율 %", example = "20")
        @NotNull(message = "할인율을 입력해 주세요.")
        // 0은 «할인 없음» 이라 행을 만들 이유가 없고(화면엔 「세일 중」인데 가격은 그대로라 고장으로 읽힌다),
        // 100은 공짜라 주문·결제·적립 계산이 전부 0원 경로로 들어간다. DB CHECK 와 같은 범위다.
        @Min(value = 1, message = "할인율은 1% 이상이어야 합니다.")
        @Max(value = 99, message = "할인율은 99% 이하여야 합니다.")
        Integer rate,

        @Schema(description = "시작일 (KST, 포함)", example = "2026-08-22")
        @NotNull(message = "시작일을 입력해 주세요.")
        LocalDate startDate,

        @Schema(description = "종료일 (KST, **포함**)", example = "2026-08-24")
        @NotNull(message = "종료일을 입력해 주세요.")
        LocalDate endDate
) {
}
