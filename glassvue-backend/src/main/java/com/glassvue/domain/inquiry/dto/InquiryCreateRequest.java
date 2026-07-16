package com.glassvue.domain.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InquiryCreateRequest(

        @Schema(description = "제목", example = "배송 문의")
        @NotBlank @Size(max = 200)
        String title,

        @Schema(description = "문의 내용", example = "언제 배송되나요?")
        @NotBlank @Size(max = 2000)
        String content,

        @Schema(description = "비밀글 여부(작성자·관리자만 열람)", example = "false")
        boolean secret
) {
}
