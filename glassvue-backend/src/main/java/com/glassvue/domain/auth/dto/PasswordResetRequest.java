package com.glassvue.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 비밀번호 재설정 요청 — 아이디로 재설정 토큰(링크) 발급. */
public record PasswordResetRequest(

        @Schema(description = "로그인 아이디", example = "hong")
        @NotBlank
        String loginId
) {
}
