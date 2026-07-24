package com.glassvue.domain.cart;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 회원별 장바구니를 Redis Hash로 저장한다. 키 cart:{memberId}, field=variantId(옵션), value=수량. (2026-07-24 C-8: productId→variantId)
 * 방치된 장바구니는 30일 뒤 만료.
 */
@Component
@RequiredArgsConstructor
public class CartStore {

    private static final String PREFIX = "cart:";
    private static final Duration TTL = Duration.ofDays(30);

    private final StringRedisTemplate redis;

    private static String key(UUID memberId) {
        return PREFIX + memberId;
    }

    /** 수량 증가(없으면 생성). */
    public void add(UUID memberId, UUID variantId, long quantity) {
        redis.opsForHash().increment(key(memberId), variantId.toString(), quantity);
        redis.expire(key(memberId), TTL);
    }

    /** 수량 지정. */
    public void set(UUID memberId, UUID variantId, long quantity) {
        redis.opsForHash().put(key(memberId), variantId.toString(), String.valueOf(quantity));
        redis.expire(key(memberId), TTL);
    }

    public void remove(UUID memberId, UUID variantId) {
        redis.opsForHash().delete(key(memberId), variantId.toString());
    }

    public void clear(UUID memberId) {
        redis.delete(key(memberId));
    }

    /** variantId → 수량 (입력 순서 유지). */
    public Map<UUID, Long> items(UUID memberId) {
        Map<Object, Object> raw = redis.opsForHash().entries(key(memberId));
        Map<UUID, Long> result = new LinkedHashMap<>();
        raw.forEach((k, v) -> result.put(UUID.fromString((String) k), Long.parseLong((String) v)));
        return result;
    }
}
