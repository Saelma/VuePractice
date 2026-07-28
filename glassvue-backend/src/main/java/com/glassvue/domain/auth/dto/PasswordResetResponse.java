package com.glassvue.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 재설정 요청 결과. 열거(존재하는 아이디인지) 공격을 막으려 결과는 항상 성공으로 답한다.
 *
 * <p>token은 메일/SMS 인프라가 없는 dev 편의용으로만 채워진다(auth.password-reset.expose-token).
 * 운영에서는 항상 null — 실제로는 여기에 담지 않고 발송 채널로 링크가 나가야 한다.
 */
public record PasswordResetResponse(

        @Schema(description = "dev 전용 노출 토큰(운영은 null)", nullable = true)
        String token
) {
    public static PasswordResetResponse of(String token) {
        return new PasswordResetResponse(token);
    }
}
