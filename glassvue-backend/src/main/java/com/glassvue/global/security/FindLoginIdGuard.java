package com.glassvue.global.security;

import java.time.Duration;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 아이디 찾기 <b>요청</b> 제한 (2026-07-31, G-1) — {@link PasswordResetRequestGuard} 와 같은 성질이다.
 *
 * <p>{@code POST /api/auth/find-id} 도 <b>열거 방지를 위해 항상 200</b> 이고 <b>성공하면 메일이 한 통</b>
 * 나간다. 즉 제한이 없으면 남의 주소만 알면 그 사람 메일함에 무한히 보낼 수 있다 — 재설정 요청에서
 * 막았던 것과 <b>같은 구멍이 같은 모양으로</b> 생긴다.
 *
 * <p><b>왜 재설정 가드를 그냥 쓰지 않는가</b> — 세는 대상이 다르다. 저쪽 키는 <b>아이디</b>고 여기는
 * <b>이메일</b>이라 애초에 같은 카운터에 못 넣는다. 억지로 합치면 더 나쁘다: 아이디 찾기를 세 번 하면
 * <b>비밀번호 재설정 예산까지 소진</b>돼, 아이디를 되찾은 사람이 곧바로 비밀번호를 못 바꾼다.
 * 공유하는 것은 카운터 구현({@link AttemptCounter})이고, <b>정책(키·임계값)은 각자</b> 가진다.
 *
 * <p>임계값은 재설정과 같게 뒀다(3 / 10 · 10분). 성질이 같기 때문이다 — 아이디 찾기도 <b>한 번이면
 * 충분</b>하고, 비용은 실패가 아니라 <b>성공(메일 한 통)</b> 에 붙는다. 그래서 <b>성공해도 리셋하지
 * 않는다</b>(리셋하면 "요청 → 성공 → 리셋" 이 무한 반복된다).
 *
 * <p>⚠ 카운트는 <b>회원 조회 전에, 입력된 주소 그대로</b> 센다 — 없는 주소도 똑같이 잠기므로
 * 429 가 <b>가입 여부를 알려주지 않는다</b>(로그인·재설정 가드와 같은 규칙).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FindLoginIdGuard {

    private static final String EMAIL_PREFIX = "auth:find-id:email:";
    private static final String IP_PREFIX = "auth:find-id:ip:";

    /** 주소당 허용 요청 수 — 메일이 안 와서 다시 누르는 것을 감안해 3회(재설정과 같다). */
    private static final int EMAIL_MAX_REQUESTS = 3;
    /** IP당 허용 요청 수 — 공유 IP 여지를 두되, 여러 주소를 훑는 것은 막는다. */
    private static final int IP_MAX_REQUESTS = 10;
    private static final int WINDOW_MINUTES = 10;

    private final AttemptCounter counter;

    /** 제한을 넘었으면 true. 회원 조회 <b>전</b>에 부른다. */
    public boolean isBlocked(String email, String clientIp) {
        return counter.get(EMAIL_PREFIX + normalize(email)) >= EMAIL_MAX_REQUESTS
                || counter.get(IP_PREFIX + clientIp) >= IP_MAX_REQUESTS;
    }

    /** 요청 하나를 기록한다. 실패가 아니라 <b>모든 요청</b>을 센다(클래스 주석). */
    public void record(String email, String clientIp) {
        Duration window = Duration.ofMinutes(WINDOW_MINUTES);
        long emailCount = counter.increment(EMAIL_PREFIX + normalize(email), window);
        long ipCount = counter.increment(IP_PREFIX + clientIp, window);
        if (emailCount == EMAIL_MAX_REQUESTS || ipCount == IP_MAX_REQUESTS) {
            log.warn("Find-login-id requests throttled: emailCount={} ip={} ipCount={}",
                    emailCount, clientIp, ipCount);
            // ⚠ 주소 자체는 로그에 남기지 않는다 — 아이디와 달리 이메일은 그 자체가 개인정보다.
        }
    }

    /**
     * 저장 규칙과 같은 정규화(trim + 소문자). 안 맞추면 <b>대문자로 보낸 요청이 다른 카운터</b>를 써서
     * 제한을 그냥 우회한다 — `Member.normalizeEmail` 과 같은 규칙을 여기서도 쓰는 이유다.
     */
    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
