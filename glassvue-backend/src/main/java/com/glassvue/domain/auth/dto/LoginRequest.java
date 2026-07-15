package com.glassvue.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @Schema(description = "로그인 아이디", example = "hong")
        @NotBlank
        String loginId,

        @Schema(description = "비밀번호", example = "password123")
        @NotBlank
        String password
) {
}
