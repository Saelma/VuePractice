package com.glassvue.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 마케팅 발송 결과 (2026-08-03, B-21 후속).
 *
 * <p>⚠ <b>세 숫자를 나눠서 준다.</b> "보냈습니다" 한 줄로 끝내면 관리자는 <b>동의자가 적어서 적게 간
 * 것</b>과 <b>다들 수신을 꺼서 적게 간 것</b>을 구분할 수 없다 — 둘은 대응이 완전히 다르다
 * (전자는 동의를 더 받아야 하고, 후자는 보내는 내용을 손봐야 한다).
 *
 * @param agreed   마케팅 수신에 <b>동의한</b> 회원 수 (근거를 가진 모집단)
 * @param sent     <b>실제로 알림이 만들어진</b> 수
 * @param optedOut 동의는 했지만 <b>알림 설정에서 꺼</b> 빠진 수 ({@code agreed - sent})
 */
public record MarketingSendResponse(
        @Schema(description = "마케팅 동의 회원 수") int agreed,
        @Schema(description = "실제 발송된 수") int sent,
        @Schema(description = "동의했지만 수신을 꺼서 제외된 수") int optedOut
) {
    public static MarketingSendResponse of(int agreed, int sent) {
        return new MarketingSendResponse(agreed, sent, agreed - sent);
    }
}
