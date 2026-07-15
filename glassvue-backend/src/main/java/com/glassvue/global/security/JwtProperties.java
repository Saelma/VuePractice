package com.glassvue.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** jwt.* 설정. secret은 .env(JWT_SECRET, Base64)에서 주입. */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long accessTokenValidityMs,
        long refreshTokenValidityMs
) {
}
