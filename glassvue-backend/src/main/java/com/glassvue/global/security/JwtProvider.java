package com.glassvue.global.security;

import com.glassvue.domain.member.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/** JWT 발급 · 파싱. HMAC 서명(키 길이에 따라 HS256/384/512 자동). */
@Component
public class JwtProvider {

    private final SecretKey key;
    private final long accessMs;
    private final long refreshMs;

    public JwtProvider(JwtProperties props) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(props.secret()));
        this.accessMs = props.accessTokenValidityMs();
        this.refreshMs = props.refreshTokenValidityMs();
    }

    public String createAccessToken(UUID memberId, Role role, String nickname) {
        return build(memberId, accessMs, Map.of("role", role.name(), "nickname", nickname));
    }

    public String createRefreshToken(UUID memberId) {
        return build(memberId, refreshMs, Map.of());
    }

    private String build(UUID memberId, long ttlMs, Map<String, Object> claims) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(memberId.toString())
                .id(UUID.randomUUID().toString()) // jti (블랙리스트용)
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(ttlMs)))
                .signWith(key)
                .compact();
    }

    /** 서명·만료 검증 후 Claims 반환. 실패 시 JwtException 계열 throw. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
