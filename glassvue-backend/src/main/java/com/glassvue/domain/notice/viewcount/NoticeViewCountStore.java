package com.glassvue.domain.notice.viewcount;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 조회수 Redis 카운터. 조회는 DB 대신 Redis에 누적하고(INCR),
 * 읽을 때 "DB 값 + 미반영분"을 합쳐 보여준다. 누적분은 Flusher가 주기적으로 DB에 반영한다.
 * 키: notice:view:{uuid}
 */
@Component
@RequiredArgsConstructor
public class NoticeViewCountStore {

    private static final String KEY_PREFIX = "notice:view:";

    private final StringRedisTemplate redis;

    private static String key(UUID id) {
        return KEY_PREFIX + id;
    }

    /** 조회수 +1 (Redis INCR) */
    public void increment(UUID id) {
        redis.opsForValue().increment(key(id));
    }

    /** 단건 미반영분 */
    public long getPending(UUID id) {
        String v = redis.opsForValue().get(key(id));
        return v == null ? 0L : Long.parseLong(v);
    }

    /** 여러 건 미반영분 (목록용, MGET 한 번) */
    public Map<UUID, Long> getPending(List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<String> keys = ids.stream().map(NoticeViewCountStore::key).toList();
        List<String> values = redis.opsForValue().multiGet(keys);
        Map<UUID, Long> result = new HashMap<>();
        if (values != null) {
            for (int i = 0; i < ids.size(); i++) {
                String v = values.get(i);
                if (v != null) {
                    result.put(ids.get(i), Long.parseLong(v));
                }
            }
        }
        return result;
    }

    /**
     * 미반영분 전체를 원자적으로 꺼내고(GETDEL) 비운다. 플러시 전용.
     * GETDEL 이후 들어오는 조회는 새 키에 다시 쌓이므로 유실되지 않는다.
     */
    public Map<UUID, Long> drainAll() {
        Map<UUID, Long> drained = new HashMap<>();
        ScanOptions options = ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(200).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                String k = cursor.next();
                String v = redis.opsForValue().getAndDelete(k); // GETDEL (원자적)
                if (v != null) {
                    UUID id = UUID.fromString(k.substring(KEY_PREFIX.length()));
                    drained.put(id, Long.parseLong(v));
                }
            }
        }
        return drained;
    }
}
