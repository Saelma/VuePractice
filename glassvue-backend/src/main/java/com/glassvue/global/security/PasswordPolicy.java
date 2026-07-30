package com.glassvue.global.security;

import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 정책 (E-3, 2026-07-30) — <b>길이 + 차단 목록 + 아이디·닉네임 포함 금지</b>.
 *
 * <p><b>⚠ 복잡도 규칙(대문자·특수문자 필수)은 일부러 넣지 않았다.</b> 최신 지침(NIST 800-63B)은 그 방식이
 * 오히려 <b>예측 가능한 패턴</b>({@code Password1!})을 낳는다고 본다 — 사람은 규칙을 만족시키는 가장 짧은
 * 길을 택하기 때문이다. 그래서 <b>길이를 올리고, 잘 알려진 값을 막는</b> 쪽으로 갔다.
 *
 * <p><b>왜 이 셋인가</b>:
 * <ul>
 *   <li><b>길이 {@value #MIN_LENGTH}자</b> — 8자는 흔한 조합이 현실적으로 뚫린다. ⚠ 다만 길이만으로는
 *       {@code password123}(11자)이 통과한다 — 그래서 목록이 필요하다.
 *   <li><b>흔한 목록</b>({@value #BLOCKLIST_PATH}) — 공격자가 <b>제일 먼저 넣어 보는 것</b>들만 걷어낸다.
 *       완전할 필요는 없다: 온라인 추측은 E-1({@link LoginAttemptGuard})이 아이디당 시간당 ~30회로 막는다.
 *   <li><b>아이디·닉네임 포함 금지</b> — 목록만 있으면 {@code hong1234} 같은 값이 통과하는데,
 *       그게 자기 아이디라면 <b>공격자가 제일 먼저 추측하는 통</b>이다. 목록에 담을 수 없는 종류라
 *       규칙으로 막는다.
 * </ul>
 *
 * <p><b>⚠ 기존 회원에게 소급 적용되지 않는다.</b> 저장된 것은 해시라 정책 위반 여부를 알 수도 없고,
 * 로그인은 정책을 검사하지 않는다 — <b>다음 변경 때부터</b> 적용된다(B-13 의 "신규 가입만 필수"와 같은 구조).
 * 그래서 데모 계정({@code password123})은 계속 로그인된다. 그건 사용자 결정(2026-07-29)이고 이 정책과
 * 충돌하지 않는다 — 다만 <b>그 값으로 비밀번호를 새로 정할 수는 없다.</b>
 *
 * <p>검증 위치가 <b>DTO 애노테이션이 아니라 서비스</b>인 이유: 아이디·닉네임 포함 여부는 <b>다른 필드를
 * 함께 봐야</b> 판정된다(비밀번호 변경 흐름에는 요청 본문에 아이디가 아예 없고 회원을 조회해야 안다).
 * 길이만 DTO 에 남기고(빠른 실패), 나머지는 여기서 본다.
 */
@Slf4j
@Component
public class PasswordPolicy {

    /** DTO 의 {@code @Size(min = ...)} 와 반드시 같은 값이어야 한다(양쪽 다 두는 이유는 클래스 주석 참조). */
    public static final int MIN_LENGTH = 10;
    private static final String BLOCKLIST_PATH = "security/common-passwords.txt";

    private final Set<String> blocklist;

    public PasswordPolicy() {
        this.blocklist = loadBlocklist();
        log.info("Password blocklist loaded: {} entries", blocklist.size());
    }

    /**
     * 새로 정하려는 비밀번호를 검증한다. 위반이면 {@link BusinessException}.
     *
     * @param password 평문 새 비밀번호
     * @param loginId  그 회원의 로그인 아이디(모르면 null)
     * @param nickname 그 회원의 닉네임(모르면 null)
     */
    public void validate(String password, String loginId, String nickname) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new BusinessException(ErrorCode.WEAK_PASSWORD_TOO_SHORT);
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (blocklist.contains(lower)) {
            throw new BusinessException(ErrorCode.WEAK_PASSWORD_COMMON);
        }
        // ⚠ 양방향으로 본다: 비밀번호가 아이디를 품는 경우(hong1234)와, 아이디가 비밀번호를 품는 경우
        // (아이디 hongpassword / 비밀번호 password) 둘 다 "아이디로 추측 가능" 이다.
        if (containsEachOther(lower, loginId) || containsEachOther(lower, nickname)) {
            throw new BusinessException(ErrorCode.WEAK_PASSWORD_CONTAINS_ID);
        }
    }

    private boolean containsEachOther(String lowerPassword, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lowerValue = value.toLowerCase(Locale.ROOT);
        // ⚠ 3자 미만은 보지 않는다 — 두 글자 닉네임("김"·"준") 때문에 정상 비밀번호가 통째로 막힌다.
        if (lowerValue.length() < 3) {
            return false;
        }
        return lowerPassword.contains(lowerValue) || lowerValue.contains(lowerPassword);
    }

    private Set<String> loadBlocklist() {
        Set<String> loaded = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource(BLOCKLIST_PATH).getInputStream(),
                        StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim().toLowerCase(Locale.ROOT);
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    loaded.add(trimmed);
                }
            }
        } catch (IOException e) {
            // ⚠ 목록을 못 읽으면 **정책이 조용히 약해진다**(길이 검사만 남는다). 그건 보안 기능이
            // 있는 척하는 상태라 더 나쁘다 → 기동을 실패시켜 드러낸다(E-2 의 fail-closed 와 같은 판단).
            throw new IllegalStateException("비밀번호 차단 목록을 읽을 수 없다: " + BLOCKLIST_PATH, e);
        }
        return Set.copyOf(loaded);
    }
}
