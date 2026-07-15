package com.glassvue.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NicknameUpdateRequest(

        @Schema(description = "새 닉네임", example = "새길동")
        @NotBlank @Size(max = 50)
        String nickname
) {
}
