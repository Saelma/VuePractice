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
        @NotBlank @Size(min = 8, max = 64)
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
        String email
) {
}
