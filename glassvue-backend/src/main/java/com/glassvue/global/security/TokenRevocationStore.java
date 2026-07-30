package com.glassvue.global.security;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 회원의 <b>이미 발급된 access 토큰을 한꺼번에 무효화</b>한다 — 발급시각 컷오프 방식.
 *
 * <p>왜 블랙리스트로는 안 되는가: {@link TokenBlacklist} 는 <b>jti 단위</b>라 "이 토큰 하나"만 막는다.
 * 회원이 여러 기기에 로그인해 있으면 서버는 그 jti 들을 알지 못하므로 <b>전부 막을 방법이 없다.</b>
 * 그래서 토큰을 모으는 대신 <b>시각 하나</b>를 남긴다 — 이 시각 이전에 발급된 것은 전부 무효다.
 *
 * <p>역할이 JWT 클레임에 박혀 있어 이게 특히 중요하다: 강등해도 옛 토큰의 {@code role} 은 여전히
 * ADMIN 이므로, 컷오프가 없으면 <b>강등된 관리자가 access 만료까지 관리자 권한을 계속 쓴다</b>
 * (2026-07-30 실측: {@code changeRole} 은 refresh 삭제조차 하지 않았다).
 *
 * <p>TTL 은 <b>access 토큰 유효기간</b>만큼만 둔다({@link TokenBlacklist} 가 "남은 만료시간만큼만"
 * 올리는 것과 같은 판단 — 키가 무한히 늘지 않는다). 그 시간이 지나면 컷오프 이전에 발급된 토큰은
 * 전부 자연 만료되므로 컷오프가 남아 있을 이유가 없다.
 */
@Component
@RequiredArgsConstructor
public class TokenRevocationStore {

    private static final String PREFIX = "auth:revoked-before:";

    private final StringRedisTemplate redis;
    private final JwtProperties props;

    /** 지금 이전에 발급된 이 회원의 access 토큰을 전부 무효화한다(정지·강등·탈퇴·비밀번호 변경). */
    public void revokeAll(UUID memberId) {
        redis.opsForValue().set(PREFIX + memberId,
                Long.toString(Instant.now().getEpochSecond()),
                Duration.ofMillis(props.accessTokenValidityMs()));
    }

    /**
     * 이 토큰이 컷오프에 걸리는가. 컷오프가 없으면 false(정상 통과).
     *
     * <p>⚠ 경계는 <b>fail-closed</b>({@code iat <= cutoff} 를 무효로 본다). JWT 의 {@code iat} 는
     * <b>초 정밀도</b>라, {@code <} 로 비교하면 <b>강등과 같은 초에 발급된 토큰이 살아남는다.</b>
     * 대가는 그 1초 사이에 로그인한 사람이 한 번 더 로그인해야 하는 것뿐이다.
     */
    public boolean isRevoked(UUID memberId, Instant issuedAt) {
        String cutoff = redis.opsForValue().get(PREFIX + memberId);
        if (cutoff == null) {
            return false;
        }
        return issuedAt.getEpochSecond() <= Long.parseLong(cutoff);
    }
}
