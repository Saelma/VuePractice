package com.glassvue.domain.member.entity;

import com.glassvue.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String loginId;

    @Column(nullable = false)
    private String password; // BCrypt 해시

    @Column(nullable = false, unique = true, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    // 이메일(2026-07-28 V29 컬럼 → 2026-07-29 B-13 수집 시작). 계정 식별자라 유일(uk_member_email).
    //
    // ⚠ **DB 는 계속 nullable 이다.** 신규 가입은 필수로 받지만(SignupRequest 의 @NotBlank),
    // 기존 회원은 값이 없어 NOT NULL 을 걸 수 없다 — 채우는 경로가 설정 화면뿐이라 백필할 값도 없다.
    // "신규는 필수"는 **API 계층의 규칙**이고 스키마 제약이 아니다. 전원이 채워진 뒤에나 NOT NULL 을 검토한다.
    //
    // ⚠ 저장 값은 **소문자·trim 으로 정규화**된다(AuthService·MemberService). Oracle UNIQUE 는
    // 대소문자를 구분해서, 정규화하지 않으면 A@b.com 과 a@b.com 이 둘 다 들어간다 — 같은 사람에게
    // 메일이 두 번 가거나 재설정 링크가 엉뚱한 계정으로 간다.
    @Column(unique = true, length = 255)
    private String email;

    /**
     * 이 주소의 <b>소유가 확인됐는지</b>(V34, 2026-07-29 B-14). 확인 메일의 인증번호를 맞혀야 true.
     *
     * <p>⚠ <b>인증은 "그 주소"에 대한 것이지 회원에 대한 게 아니다</b> — 주소를 바꾸면
     * {@link #updateEmail}이 이 값을 <b>false 로 되돌린다.</b> 안 그러면 인증된 주소를 미인증 주소로
     * 갈아끼워 "인증됨" 딱지만 물려받을 수 있다.
     */
    @Column(nullable = false)
    private boolean emailVerified;

    // 회원 정지(B-11 후속, 2026-07-28, V30). 정지되면 로그인·토큰갱신·주문이 막힌다(관리자만 조작).
    // NUMBER(1) DEFAULT 0 — 기존 회원은 활성(false).
    @Column(nullable = false)
    private boolean suspended;

    /**
     * 이용약관 + 개인정보 처리방침에 동의한 시각 (V37, 2026-08-03 B-21). <b>필수 동의</b>.
     *
     * <p>⚠ <b>{@code null} 이 정상값이다</b> — V37 이전 가입자는 동의 절차 자체가 없었다.
     * 여기에 백필을 했으면 <b>동의한 적 없는 사람에게 동의 시각이 생겼을 것</b>이고, 하필 이 값은
     * "동의를 받았다"는 <b>근거</b>로 쓸 것이라 거짓이 가장 비싼 자리다. {@code null} = 기록 없음.
     *
     * <p>⚠ 시각은 <b>서버가 찍는다</b>(가입 처리 시점). 클라이언트가 보낸 시각을 믿으면
     * 동의 기록이 조작 가능해진다 — 근거로 쓸 값이라 출처가 서버여야 한다.
     *
     * <p>약관과 개인정보 처리방침을 <b>한 필드로 합친 것은 의도적</b>이다. 둘 다 필수라 항상 같이
     * 참/거짓이고, 나누면 <i>"약관만 동의한 회원"</i> 이라는 <b>일어날 수 없는 상태</b>가 표현 가능해진다.
     */
    @Column(name = "terms_agreed_at")
    private Instant termsAgreedAt;

    /**
     * 마케팅 수신에 동의한 시각 (V37, B-21). <b>선택 동의</b> — {@code null} 이면 미동의.
     *
     * <p>⚠ <b>지금 이 값을 읽어 무언가를 보내는 코드는 없다.</b> 그래도 받는 이유는
     * <b>동의는 소급해서 받을 수 없기 때문</b>이다 — 발송 채널이 생긴 뒤에 "그때 동의했나"를
     * 물어볼 방법이 없다.
     *
     * <p>같은 이유로 {@code NotificationType} 에 {@code MARKETING} 을 <b>더하지 않았다</b>:
     * 설정 화면이 {@code values()} 를 통째로 그리므로 <b>아무 일도 안 하는 토글</b>이 생긴다.
     * 죽은 배선과 남겨 둔 근거는 다르다.
     */
    @Column(name = "marketing_agreed_at")
    private Instant marketingAgreedAt;

    // --- 기본 배송지는 여기 없다 (2026-07-24, V18) ---
    //
    // V11~V17 동안은 ship_recipient·ship_phone·ship_zipcode·ship_address1·ship_address2 5컬럼이
    // 여기 있었다(회원당 배송지 하나). 주소록(MemberAddress)으로 옮기면서 **매핑을 걷어냈다.**
    //
    // DB 컬럼은 아직 남아 있다 — 운영 구 jar 가 그 컬럼을 매핑하고 있어서 지금 DROP 하면 재기동이
    // 불가능해진다(V18 주석의 expand/contract 참조). 컬럼 DROP 은 V19 로 미뤘다.
    //
    // 그동안 "신 코드는 member.ship_* 에 쓰지 않는다" 를 규율이 아니라 **구조로** 보장한다 —
    // 필드가 없으면 쓸 방법이 없다. ddl-auto=validate 는 매핑되지 않은 여분 컬럼을 문제 삼지 않는다.

    // email 은 builder 에서 선택이다 — 테스트 픽스처처럼 이메일이 무의미한 자리가 많고,
    // "신규 가입은 필수" 규칙은 SignupRequest 검증이 이미 책임진다(여기서 또 막으면 이중 규칙).
    // 동의 시각도 builder 에서 선택이다 — 테스트 픽스처(리포지토리 직접 저장)는 **V37 이전 회원을
    // 재현하는 자리**라 값이 없는 게 맞다. "신규 가입은 필수" 규칙은 AuthService.signup 이 책임진다
    // (email 을 선택으로 둔 것과 같은 판단 — 여기서 또 막으면 이중 규칙이 된다, WA §3 픽스처 두 갈래).
    @Builder
    private Member(String loginId, String password, String nickname, Role role, String email,
                   Instant termsAgreedAt, Instant marketingAgreedAt) {
        this.loginId = loginId;
        this.password = password;
        this.nickname = nickname;
        this.role = role;
        this.email = email;
        this.termsAgreedAt = termsAgreedAt;
        this.marketingAgreedAt = marketingAgreedAt;
    }

    /** 약관에 동의했는가 — 시각이 남아 있으면 동의한 것이다(V37 이전 가입자는 false). */
    public boolean hasAgreedToTerms() {
        return termsAgreedAt != null;
    }

    /** 마케팅 수신에 동의했는가. 발송 채널이 생기면 이 값을 보고 대상을 고른다. */
    public boolean hasAgreedToMarketing() {
        return marketingAgreedAt != null;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    /**
     * 이메일 변경. ⚠ <b>인증 상태가 함께 풀린다</b>(B-14) — 새 주소는 아직 소유가 확인되지 않았다.
     * 같은 값을 다시 저장하는 경우에도 푼다: 굳이 예외를 두면 "언제 유지되는지"가 규칙이 되어
     * 나중에 어긋난다(재인증은 메일 한 통이라 비용이 작다).
     */
    public void updateEmail(String email) {
        this.email = email;
        this.emailVerified = false;
    }

    /** 인증번호 확인에 성공했을 때만 호출된다(MemberService). */
    public void verifyEmail() {
        this.emailVerified = true;
    }

    /**
     * 이메일 정규화 — trim + 소문자(B-13). <b>저장과 중복검사가 반드시 같은 형태를 써야</b> 하므로
     * 규칙을 한 곳에 둔다(가입은 AuthService, 변경은 MemberService — 두 곳이 각자 정규화하면 어긋난다).
     *
     * <p>⚠ {@link Locale#ROOT} 를 명시한다. 기본 로케일이 터키어면 {@code "I".toLowerCase()} 가
     * 점 없는 'ı' 가 돼(터키 I 문제) 같은 주소가 서버 설정에 따라 다른 값으로 저장된다.
     */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    // --- 관리자 조작(B-11 후속) ---

    public void changeRole(Role role) {
        this.role = role;
    }

    public void suspend() {
        this.suspended = true;
    }

    public void unsuspend() {
        this.suspended = false;
    }
}
