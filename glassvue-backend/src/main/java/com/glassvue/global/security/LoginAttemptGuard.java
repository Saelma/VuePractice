package com.glassvue.global.security;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 로그인 무차별 대입 방어 (E-1, 2026-07-30) — <b>아이디 기준 + IP 기준</b> 두 카운터.
 *
 * <p>왜 필요했나: {@code POST /api/auth/login} 에 시도 제한이 <b>전혀 없었다</b>(실측). 비밀번호 최소
 * 길이도 8자뿐이라 흔한 조합은 현실적으로 뚫린다. ⚠ 더 이상한 건 <b>비대칭</b>이었다 — 이메일 인증번호
 * (6자리)엔 {@link EmailVerificationCodeStore} 가 5회 제한을 걸어 뒀는데, 정작 더 중요한 비밀번호엔 없었다.
 *
 * <p><b>왜 둘 다 세는가</b>: 아이디만 막으면 <b>한 IP 가 여러 계정을 훑을 수 있고</b>(계정마다 5회씩),
 * IP 만 막으면 <b>공유 IP(가정·사무실)에서 선의의 사용자가 함께 막힌다</b>. 그래서 둘을 각각 센다.
 *
 * <p><b>⚠ 성공은 아이디 카운터만 지운다.</b> IP 카운터까지 지우면, 계정 하나를 가진 공격자가
 * <b>자기 계정으로 로그인해 IP 예산을 리셋</b>하고 남의 계정 훑기를 계속할 수 있다.
 *
 * <p><b>⚠ 카운트는 DB 조회보다 먼저, 입력된 아이디 그대로 한다</b> — 없는 아이디도 똑같이 잠긴다.
 * 그래서 차단 응답({@code AUTH-429})이 <b>계정 존재를 알려주지 않는다.</b> 백로그 E-1 은 *"차단 사실을
 * 드러내면 계정 존재를 알려주는 셈"* 이라 적어 뒀지만, 그건 <b>존재하는 계정만 셀 때</b> 성립하는 얘기다.
 * 존재 여부와 무관하게 세면 응답이 대칭이 되므로, 숨기지 않고 알려 주는 쪽이 낫다(사용자 결정 2026-07-30).
 *
 * <p><b>차단은 윈도우가 지나면 자동으로 풀린다</b>({@value #WINDOW_MINUTES}분). 시도할 때마다 TTL 을
 * 연장하지 않는 것은 의도다 — 연장하면 공격자가 계속 두드려 <b>선의의 사용자를 영구히 잠글 수 있다</b>.
 * 대가는 아이디당 시간당 약 30회를 허용하는 것인데, 그 정도면 무차별 대입은 성립하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAttemptGuard {

    private static final String ID_PREFIX = "auth:login-fail:id:";
    private static final String IP_PREFIX = "auth:login-fail:ip:";

    /** 아이디당 허용 실패 횟수. 사람이 오타로 틀리는 건 보통 3회 이하라 여유가 있다. */
    private static final int ID_MAX_FAILURES = 5;
    /** IP당 허용 실패 횟수 — 공유 IP 를 감안해 넉넉히 둔다(한 IP 에서 여러 사람이 접속할 수 있다). */
    private static final int IP_MAX_FAILURES = 20;
    private static final int WINDOW_MINUTES = 10;

    private final StringRedisTemplate redis;

    /** 차단 상태면 true. 로그인 처리 <b>맨 앞</b>에서 부른다(DB 조회 전 — 존재 여부를 안 건드린다). */
    public boolean isBlocked(String loginId, String clientIp) {
        return count(ID_PREFIX + normalize(loginId)) >= ID_MAX_FAILURES
                || count(IP_PREFIX + clientIp) >= IP_MAX_FAILURES;
    }

    /** 로그인 실패를 기록한다(아이디·IP 양쪽). */
    public void recordFailure(String loginId, String clientIp) {
        long idFails = increment(ID_PREFIX + normalize(loginId));
        long ipFails = increment(IP_PREFIX + clientIp);
        if (idFails == ID_MAX_FAILURES || ipFails == IP_MAX_FAILURES) {
            // 임계값에 닿은 순간만 남긴다 — 매 실패를 warn 으로 남기면 로그가 공격자에게 도배된다.
            // ⚠ 아이디는 남기고 비밀번호는 절대 남기지 않는다(로그가 자격증명 저장소가 되면 안 된다).
            log.warn("Login attempts blocked: loginId={} idFails={} ip={} ipFails={}",
                    loginId, idFails, clientIp, ipFails);
        }
    }

    /** 로그인 성공 — <b>아이디 카운터만</b> 지운다(위 클래스 주석의 IP 예산 리셋 문제). */
    public void recordSuccess(String loginId) {
        redis.delete(ID_PREFIX + normalize(loginId));
    }

    private long count(String key) {
        String value = redis.opsForValue().get(key);
        return value == null ? 0L : Long.parseLong(value);
    }

    private long increment(String key) {
        Long value = redis.opsForValue().increment(key);
        if (value != null && value == 1L) {
            // 첫 실패에만 TTL 을 건다 — 매번 걸면 시도할 때마다 창이 밀려 영구 차단이 된다.
            // (인증번호 시도 카운터와 같은 방식 — 안 걸면 반대로 카운터가 영원히 남는다.)
            redis.expire(key, Duration.ofMinutes(WINDOW_MINUTES));
        }
        return value == null ? 0L : value;
    }

    /**
     * 아이디 정규화 — 대소문자만 맞춘다.
     *
     * <p>⚠ 안 하면 {@code Hong} / {@code hong} 이 <b>다른 카운터</b>가 되어 대소문자만 바꿔 가며
     * 제한을 우회할 수 있다. 로그인 조회 자체는 대소문자를 구분하지만(유니크 제약도 그렇다),
     * <b>방어는 더 넓게 잡아야</b> 우회가 안 생긴다. 이메일 정규화(B-13)와 같은 판단이다.
     */
    private String normalize(String loginId) {
        return loginId == null ? "" : loginId.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
