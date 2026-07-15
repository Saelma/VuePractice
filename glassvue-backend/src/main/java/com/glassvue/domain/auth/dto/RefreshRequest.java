package com.glassvue.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(

        @Schema(description = "리프레시 토큰")
        @NotBlank
        String refreshToken
) {
}
