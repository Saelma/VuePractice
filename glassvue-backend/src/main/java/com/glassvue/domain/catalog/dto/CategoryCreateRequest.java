package com.glassvue.domain.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryCreateRequest(

        @Schema(description = "카테고리 이름", example = "전자기기")
        @NotBlank @Size(max = 50)
        String name
) {
}
