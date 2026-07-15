package com.glassvue.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
        String nickname
) {
}
