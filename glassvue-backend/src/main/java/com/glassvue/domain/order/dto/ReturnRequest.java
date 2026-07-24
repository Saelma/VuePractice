package com.glassvue.domain.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 반품 요청 — 사유를 받는다(관리자가 승인 판단에 참고). */
public record ReturnRequest(
        @Schema(description = "반품 사유", example = "단순 변심")
        @NotBlank @Size(max = 500)
        String reason
) {
}
