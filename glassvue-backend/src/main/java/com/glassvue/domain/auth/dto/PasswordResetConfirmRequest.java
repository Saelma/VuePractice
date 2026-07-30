package com.glassvue.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 재설정 토큰 + 새 비밀번호로 실제 변경. */
public record PasswordResetConfirmRequest(

        @Schema(description = "재설정 토큰", example = "018f...")
        @NotBlank
        String token,

        @Schema(description = "새 비밀번호", example = "newpassword123")
        @NotBlank @Size(min = 10, max = 64)
        String newPassword
) {
}
