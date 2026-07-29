package com.glassvue.global.security;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 이메일 소유 확인용 인증번호 (B-14, 2026-07-29). key=`auth:email-verify:&lt;memberId&gt;`, value=6자리 숫자.
 *
 * <p><b>⚠ 재설정 토큰({@link PasswordResetTokenStore})과 키 방향이 반대다.</b> 저쪽은 토큰이 키다
 * (링크로 전달되므로 토큰만으로 대상을 찾아야 한다). 여기는 <b>로그인한 본인이 자기 주소를 인증</b>하는
 * 흐름이라 memberId 를 이미 알고 있고, 오히려 <b>회원별로 묶여 있어야</b> 남의 코드를 맞혀 볼 수 없다.
 *
 * <p><b>⚠ 6자리는 100만 가지뿐이라 무차별 대입이 현실적이다.</b> 인증에 성공하면 "이 주소는 내 것"이
 * 되므로, 남의 주소를 넣고 코드를 찍어 맞히면 <b>소유하지도 않은 주소가 인증된다.</b> 그래서:
 * <ul>
 *   <li><b>시도 횟수 제한</b> — {@value #MAX_ATTEMPTS}회 틀리면 코드를 <b>폐기</b>한다(다시 발송해야 한다).
 *       이러면 100만 가지 중 5번만 볼 수 있어 성공 확률이 사실상 0이 된다.</li>
 *   <li><b>짧은 TTL</b> — {@value #VALIDITY_MINUTES}분. 재설정 토큰(30분)보다 짧다. 인증번호는
 *       메일을 열어 바로 입력하는 것이라 길 이유가 없고, 짧을수록 시도할 시간도 준다.</li>
 *   <li><b>{@link SecureRandom}</b> — 예측 가능한 난수면 제한이 무의미하다.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class EmailVerificationCodeStore {

    private static final String PREFIX = "auth:email-verify:";
    private static final String ATTEMPT_PREFIX = "auth:email-verify:attempt:";
    private static final int MAX_ATTEMPTS = 5;
    private static final int VALIDITY_MINUTES = 10;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    /** 6자리 인증번호를 새로 발급한다(재발급하면 이전 것은 덮여 무효, 시도 횟수도 초기화). */
    public String issue(UUID memberId) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        redis.opsForValue().set(PREFIX + memberId, code, Duration.ofMinutes(VALIDITY_MINUTES));
        redis.delete(ATTEMPT_PREFIX + memberId); // 재발송은 시도 횟수를 리셋한다
        return code;
    }

    /**
     * 인증번호를 검증한다. 맞으면 코드를 <b>소비(삭제)</b>하고 true.
     *
     * <p>틀리면 시도 횟수를 올리고, {@value #MAX_ATTEMPTS}회를 넘으면 <b>코드를 폐기</b>한다 —
     * 그 뒤로는 맞는 값을 넣어도 실패한다(재발송해야 한다).
     */
    public boolean verify(UUID memberId, String code) {
        String stored = redis.opsForValue().get(PREFIX + memberId);
        if (stored == null || code == null) {
            return false;
        }
        if (stored.equals(code)) {
            redis.delete(PREFIX + memberId);
            redis.delete(ATTEMPT_PREFIX + memberId);
            return true;
        }
        Long attempts = redis.opsForValue().increment(ATTEMPT_PREFIX + memberId);
        if (attempts != null && attempts == 1L) {
            // 첫 실패에 TTL 을 건다 — 안 걸면 카운터가 영원히 남아 다음 발급까지 오염된다.
            redis.expire(ATTEMPT_PREFIX + memberId, Duration.ofMinutes(VALIDITY_MINUTES));
        }
        if (attempts != null && attempts >= MAX_ATTEMPTS) {
            redis.delete(PREFIX + memberId); // 폐기 — 무차별 대입 차단
        }
        return false;
    }

    /** 진행 중인 인증번호가 있는지(화면이 "입력 대기" 상태를 그릴 때). */
    public boolean hasPending(UUID memberId) {
        return Boolean.TRUE.equals(redis.hasKey(PREFIX + memberId));
    }
}
