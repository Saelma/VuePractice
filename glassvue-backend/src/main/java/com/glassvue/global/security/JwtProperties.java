package com.glassvue.global.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** jwt.* 설정. secret은 .env(JWT_SECRET, Base64)에서 주입. */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        /** 🔴 비면 서명 키가 없다 — 기동할 때 알아야 한다(요청이 올 때가 아니라). */
        @NotBlank String secret,
        /** 🔴 <b>0 이면 토큰이 발급 즉시 만료</b> — 아무도 로그인 상태를 유지 못 한다. */
        @Positive long accessTokenValidityMs,
        /** 🔴 <b>0 이면 재발급이 불가</b> — 30분마다 다시 로그인해야 한다. */
        @Positive long refreshTokenValidityMs
) {
}
