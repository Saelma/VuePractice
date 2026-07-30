package com.glassvue.global.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 비밀번호 정책 (E-3) 단위 테스트 — 경계는 단위로 본다(통합은 행복 경로를 본다).
 *
 * <p>⚠ 이 테스트가 지키는 핵심은 <b>"길이만으로는 안 걸리는 값"</b>이 목록에 걸린다는 것이다:
 * {@code password123} 은 11자라 길이 검사를 통과한다. 그래서 두 장치가 <b>짝</b>이어야 한다.
 */
class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    private void assertRejected(String password, String loginId, String nickname, ErrorCode expected) {
        assertThatThrownBy(() -> policy.validate(password, loginId, nickname))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("정상 비밀번호는 통과한다")
    void accepts() {
        assertThatCode(() -> policy.validate("Tulip-Harbor-72", "hong", "홍길동"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("10자 미만 → TOO_SHORT (null 도 같은 취급)")
    void rejectsShort() {
        assertRejected("abc12345", "hong", "홍길동", ErrorCode.WEAK_PASSWORD_TOO_SHORT);   // 8자 — 예전 하한
        assertRejected("abc123456", "hong", "홍길동", ErrorCode.WEAK_PASSWORD_TOO_SHORT);  // 9자 — 경계
        assertRejected(null, "hong", "홍길동", ErrorCode.WEAK_PASSWORD_TOO_SHORT);
    }

    @Test
    @DisplayName("⚠ 길이는 통과하지만 흔한 값 → COMMON (password123 은 11자다)")
    void rejectsCommonEvenWhenLongEnough() {
        assertRejected("password123", "hong", "홍길동", ErrorCode.WEAK_PASSWORD_COMMON);
        assertRejected("1234567890", "hong", "홍길동", ErrorCode.WEAK_PASSWORD_COMMON);
        assertRejected("qwertyuiop", "hong", "홍길동", ErrorCode.WEAK_PASSWORD_COMMON);
        // 대소문자만 바꾼 우회도 막는다(목록 비교는 소문자로 한다)
        assertRejected("Password123", "hong", "홍길동", ErrorCode.WEAK_PASSWORD_COMMON);
        assertRejected("PASSWORD123", "hong", "홍길동", ErrorCode.WEAK_PASSWORD_COMMON);
    }

    @Test
    @DisplayName("아이디·닉네임을 포함하면 → CONTAINS_ID (양방향)")
    void rejectsIdentifierInside() {
        // 비밀번호가 아이디를 품는 경우 — 공격자가 제일 먼저 추측하는 통
        assertRejected("hongildong-99", "hongildong", "홍길동", ErrorCode.WEAK_PASSWORD_CONTAINS_ID);
        // 닉네임을 품는 경우
        assertRejected("홍길동-비밀번호99", "hongildong", "홍길동", ErrorCode.WEAK_PASSWORD_CONTAINS_ID);
        // 대소문자가 달라도 잡는다
        assertRejected("HongIlDong-1234", "hongildong", "홍길동", ErrorCode.WEAK_PASSWORD_CONTAINS_ID);
        // 반대 방향 — 아이디가 비밀번호를 품는 경우(아이디 hongsecretkey / 비번 secretkey)
        assertRejected("hongsecretke", "hongsecretkey", "닉", ErrorCode.WEAK_PASSWORD_CONTAINS_ID);
    }

    @Test
    @DisplayName("⚠ 2자 이하 닉네임은 포함 검사에서 제외 — 정상 비밀번호가 통째로 막히는 것을 막는다")
    void shortNicknameDoesNotBlockEverything() {
        // 닉네임이 "하" 라면, '하' 가 든 모든 비밀번호가 거부될 수 있다 — 그건 정책이 아니라 고장이다
        assertThatCode(() -> policy.validate("하늘아래-첫동네77", "someone", "하"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("아이디·닉네임이 null 이어도 검사가 깨지지 않는다")
    void nullIdentifiersAreSkipped() {
        assertThatCode(() -> policy.validate("Tulip-Harbor-72", null, null))
                .doesNotThrowAnyException();
        // 그래도 목록·길이는 그대로 적용된다
        assertRejected("password123", null, null, ErrorCode.WEAK_PASSWORD_COMMON);
    }
}
