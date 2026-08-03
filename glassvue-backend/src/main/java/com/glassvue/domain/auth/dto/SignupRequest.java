package com.glassvue.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(

        @Schema(description = "로그인 아이디", example = "hong")
        @NotBlank @Size(min = 4, max = 50)
        String loginId,

        @Schema(description = "비밀번호", example = "password123")
        @NotBlank @Size(min = 10, max = 64)
        String password,

        @Schema(description = "닉네임", example = "홍길동")
        @NotBlank @Size(max = 50)
        String nickname,

        /*
         * ⚠ 신규 가입에만 필수다(B-13, 2026-07-29). DB 컬럼은 nullable 그대로 —
         * 기존 회원은 값이 없고 백필할 출처도 없어 NOT NULL 을 걸 수 없다(Member.email 주석).
         * 즉 "필수"는 여기(API 계층)에서만 성립한다.
         */
        @Schema(description = "이메일 — 비밀번호 재설정 링크를 받을 주소", example = "hong@example.com")
        @NotBlank @Email @Size(max = 255)
        String email,

        /*
         * 이용약관 + 개인정보 처리방침 동의 (B-21, 2026-08-03). **필수**.
         *
         * ⚠ 검증을 @AssertTrue 로 하지 않고 **서비스에서** 한다(AuthService.signup).
         *   ①빈 검증 실패는 `COMMON-400`에 `agreeTerms: ...` 형태로 나가는데(GlobalExceptionHandler),
         *     그건 **형식 오류의 문구**다. 동의 누락은 형식이 아니라 **정책**이라 전용 코드를 준다
         *     (비밀번호 정책 E-3 을 DTO 가 아니라 서비스에 둔 것과 같은 판단).
         *   ②프론트가 **에러 코드로** 어느 체크박스를 붉힐지 고를 수 있다.
         *
         * ⚠ **동의 "시각"은 받지 않는다.** 클라이언트가 보낸 시각을 믿으면 근거가 조작 가능해진다 —
         *   서버가 가입 처리 시점을 찍는다.
         */
        @Schema(description = "이용약관·개인정보 처리방침 동의 (필수)", example = "true")
        Boolean agreeTerms,

        /*
         * 마케팅 수신 동의 — **선택**. 안 보내면(null) 미동의로 본다.
         * 지금 이 값으로 무언가를 보내는 코드는 없다. 동의는 소급해서 받을 수 없어 먼저 받아 둔다.
         */
        @Schema(description = "마케팅 수신 동의 (선택)", example = "false")
        Boolean agreeMarketing
) {
}
