package com.glassvue.global.security;

import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 리프레시 토큰을 Redis에 회원별 1개 저장(로그인 시 발급/회전, 로그아웃 시 삭제). */
@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String PREFIX = "auth:refresh:";

    private final StringRedisTemplate redis;
    private final JwtProperties props;

    public void save(UUID memberId, String refreshToken) {
        redis.opsForValue().set(
                PREFIX + memberId, refreshToken, Duration.ofMillis(props.refreshTokenValidityMs()));
    }

    public boolean matches(UUID memberId, String refreshToken) {
        String stored = redis.opsForValue().get(PREFIX + memberId);
        return stored != null && stored.equals(refreshToken);
    }

    public void delete(UUID memberId) {
        redis.delete(PREFIX + memberId);
    }
}
