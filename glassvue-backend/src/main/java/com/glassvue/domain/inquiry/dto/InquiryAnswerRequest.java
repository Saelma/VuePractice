package com.glassvue.domain.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InquiryAnswerRequest(

        @Schema(description = "관리자 답변", example = "내일 출고 예정입니다.")
        @NotBlank @Size(max = 2000)
        String answer
) {
}
