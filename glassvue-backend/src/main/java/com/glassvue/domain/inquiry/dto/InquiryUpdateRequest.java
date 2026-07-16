package com.glassvue.domain.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InquiryUpdateRequest(

        @Schema(description = "제목", example = "배송 문의(수정)")
        @NotBlank @Size(max = 200)
        String title,

        @Schema(description = "문의 내용", example = "내용을 수정합니다.")
        @NotBlank @Size(max = 2000)
        String content,

        @Schema(description = "비밀글 여부", example = "true")
        boolean secret
) {
}
