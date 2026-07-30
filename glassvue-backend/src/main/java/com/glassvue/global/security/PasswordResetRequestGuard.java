package com.glassvue.global.security;

import java.time.Duration;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 재설정 <b>요청</b> 제한 (2026-07-30) — 메일 폭탄과 토큰 남발을 막는다.
 *
 * <p>왜 필요했나(E-1 을 하며 드러났다): {@code POST /api/auth/password-reset/request} 는 <b>열거 방지를
 * 위해 항상 200</b> 을 돌려준다. 그래서 남의 아이디를 알면 <b>같은 주소로 재설정 메일을 무한히 보낼 수
 * 있었다</b> — 공격이라기보다 <b>괴롭히기</b>에 가깝고, 받는 사람은 막을 방법이 없다.
 *
 * <p>부수적으로 <b>토큰 남발</b>도 막는다. {@code PasswordResetTokenStore} 는 <b>토큰을 키</b>로 두므로
 * (회원당 하나가 아니다) 요청을 반복하면 <b>유효한 링크가 계속 쌓인다</b>(각 30분). 요청 수를 제한하면
 * 동시에 살아 있는 링크 수도 함께 묶인다.
 *
 * <p><b>임계값이 로그인({@link LoginAttemptGuard})보다 훨씬 낮다</b> — 성질이 다르기 때문이다:
 * 로그인 실패는 <b>본인의 오타</b>가 흔하지만, 재설정 요청은 <b>한 번이면 충분</b>하다(메일이 안 왔다면
 * 두세 번). 게다가 실패가 아니라 <b>성공한 요청 하나하나가 메일 한 통</b>이라 비용이 반대로 붙는다.
 *
 * <p>⚠ 카운트는 {@link LoginAttemptGuard} 와 같은 규칙 — <b>DB 조회 전에, 입력된 아이디 그대로</b> 센다.
 * 없는 아이디도 똑같이 잠기므로 429 응답이 <b>계정 존재를 알려주지 않는다</b>. 이 경로는 열거 방지가
 * 존재 이유인 자리라, 제한을 붙이면서 그걸 깨뜨리면 앞뒤가 안 맞는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetRequestGuard {

    private static final String ID_PREFIX = "auth:reset-req:id:";
    private static final String IP_PREFIX = "auth:reset-req:ip:";

    /** 아이디당 허용 요청 수 — 메일이 안 와서 다시 누르는 것을 감안해 3회. */
    private static final int ID_MAX_REQUESTS = 3;
    /** IP당 허용 요청 수 — 공유 IP 여지를 두되, 여러 계정을 훑는 것은 막는다. */
    private static final int IP_MAX_REQUESTS = 10;
    private static final int WINDOW_MINUTES = 10;

    private final AttemptCounter counter;

    /** 제한을 넘었으면 true. 회원 조회 <b>전</b>에 부른다. */
    public boolean isBlocked(String loginId, String clientIp) {
        return counter.get(ID_PREFIX + normalize(loginId)) >= ID_MAX_REQUESTS
                || counter.get(IP_PREFIX + clientIp) >= IP_MAX_REQUESTS;
    }

    /**
     * 요청 하나를 기록한다.
     *
     * <p>⚠ 로그인과 달리 <b>실패가 아니라 모든 요청</b>을 센다 — 여기서 비용이 드는 것은 실패가 아니라
     * <b>성공(메일 발송)</b> 이다. 그리고 <b>성공 시 리셋하지 않는다</b>: 리셋하면 "요청 → 성공 → 리셋" 이
     * 무한 반복돼 제한이 아무 의미가 없어진다.
     */
    public void record(String loginId, String clientIp) {
        Duration window = Duration.ofMinutes(WINDOW_MINUTES);
        long idCount = counter.increment(ID_PREFIX + normalize(loginId), window);
        long ipCount = counter.increment(IP_PREFIX + clientIp, window);
        if (idCount == ID_MAX_REQUESTS || ipCount == IP_MAX_REQUESTS) {
            log.warn("Password reset requests throttled: loginId={} idCount={} ip={} ipCount={}",
                    loginId, idCount, clientIp, ipCount);
        }
    }

    /** 로그인 가드와 같은 이유로 대소문자를 맞춘다(대소문자만 바꿔 우회하는 것 차단). */
    private String normalize(String loginId) {
        return loginId == null ? "" : loginId.trim().toLowerCase(Locale.ROOT);
    }
}
