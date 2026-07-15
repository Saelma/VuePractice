package com.glassvue.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordUpdateRequest(

        @Schema(description = "현재 비밀번호")
        @NotBlank
        String currentPassword,

        @Schema(description = "새 비밀번호", example = "newpassword123")
        @NotBlank @Size(min = 8, max = 64)
        String newPassword
) {
}
