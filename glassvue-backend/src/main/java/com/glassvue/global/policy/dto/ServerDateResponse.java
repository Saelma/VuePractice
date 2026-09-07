package com.glassvue.global.policy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 서버가 보는 «오늘»(KST). 기간 프리셋(«이번 달»·«지난 달»)의 <b>유일한 기준점</b>이다.
 *
 * <p>🔴 <b>브라우저 시계를 쓰지 않는 이유</b>: 시간대를 옳게 변환해도 <b>시계 자체가 틀릴 수 있고</b>,
 * 그러면 «이번 달» 이 화면마다 다른 기간을 뜻하게 된다. 장부는 KST 한 벌이므로 기준도 한 곳이어야 한다(B-26).
 */
@Schema(description = "서버 기준 오늘(KST)")
public record ServerDateResponse(
        @Schema(description = "KST 기준 오늘 날짜", example = "2026-09-07") LocalDate today) {
}
