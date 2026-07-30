package com.glassvue.global.security;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * "일정 시간 안에 몇 번" 을 세는 Redis 카운터 — 시도 제한들이 공유하는 조각.
 *
 * <p>따로 뺀 이유는 <b>TTL 을 첫 증가에만 거는 규칙</b> 하나다. 이게 두 곳에 복사되면 언젠가 한쪽에서
 * 어긋나는데, 어긋나는 방향이 <b>둘 다 나쁘다</b>:
 * <ul>
 *   <li>매번 TTL 을 걸면 → 시도할 때마다 창이 밀려 <b>사실상 영구 차단</b>이 된다(공격자가 계속 두드려
 *       선의의 사용자를 잠글 수 있다).
 *   <li>TTL 을 아예 안 걸면 → 카운터가 <b>영원히 남아</b> 한 번 임계값에 닿은 대상은 다시 못 들어온다.
 * </ul>
 *
 * <p>즉 이 클래스는 로직이 작아서 뺀 게 아니라 <b>틀리기 쉬워서</b> 뺀 것이다.
 * 쓰는 쪽({@link LoginAttemptGuard}, {@link PasswordResetRequestGuard})은 <b>임계값과 키 이름만</b> 정한다.
 */
@Component
@RequiredArgsConstructor
public class AttemptCounter {

    private final StringRedisTemplate redis;

    /** 현재 횟수(없으면 0). */
    public long get(String key) {
        String value = redis.opsForValue().get(key);
        return value == null ? 0L : Long.parseLong(value);
    }

    /** 1 증가시키고 증가 후 값을 돌려준다. <b>첫 증가에만</b> 창(TTL)을 건다(클래스 주석). */
    public long increment(String key, Duration window) {
        Long value = redis.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redis.expire(key, window);
        }
        return value == null ? 0L : value;
    }

    /** 카운터를 지운다(예: 로그인 성공). */
    public void clear(String key) {
        redis.delete(key);
    }
}
