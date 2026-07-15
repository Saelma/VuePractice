package com.glassvue.global.security;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 로그아웃된 access 토큰의 jti를 남은 만료시간만큼 블랙리스트에 올린다. */
@Component
@RequiredArgsConstructor
public class TokenBlacklist {

    private static final String PREFIX = "auth:blacklist:";

    private final StringRedisTemplate redis;

    public void blacklist(String jti, long ttlMillis) {
        if (ttlMillis <= 0) {
            return; // 이미 만료된 토큰은 올릴 필요 없음
        }
        redis.opsForValue().set(PREFIX + jti, "1", Duration.ofMillis(ttlMillis));
    }

    public boolean contains(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(PREFIX + jti));
    }
}
