package com.glassvue.global.security;

import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 재설정 토큰을 Redis에 저장한다. key=auth:reset:&lt;token&gt;, value=memberId.
 *
 * <p>refresh 토큰과 반대로 <b>토큰을 키</b>로 둔다 — 회원당 1개가 아니라 토큰당 1개라야
 * 재발급이 이전 링크를 조용히 무효화하지 않고, 소비 시 토큰만으로 O(1) 삭제된다.
 * consume은 getAndDelete로 <b>단발성</b>을 보장한다(한 번 쓰면 사라져 재사용·경합 불가).
 */
@Component
@RequiredArgsConstructor
public class PasswordResetTokenStore {

    private static final String PREFIX = "auth:reset:";

    private final StringRedisTemplate redis;
    private final PasswordResetProperties props;

    /** 회원용 재설정 토큰을 새로 만들어 TTL과 함께 저장하고, 그 토큰 문자열을 돌려준다. */
    public String issue(UUID memberId) {
        String token = UuidCreator.getTimeOrderedEpoch().toString();
        redis.opsForValue().set(
                PREFIX + token, memberId.toString(), Duration.ofMillis(props.tokenValidityMs()));
        return token;
    }

    /**
     * 토큰을 소비한다 — 유효하면 대상 회원 id를 돌려주고 즉시 삭제, 없거나 만료면 null.
     * getAndDelete라 동시에 두 번 소비될 수 없다(둘 중 하나만 값을 받는다).
     */
    public UUID consume(String token) {
        String memberId = redis.opsForValue().getAndDelete(PREFIX + token);
        return memberId == null ? null : UUID.fromString(memberId);
    }
}
