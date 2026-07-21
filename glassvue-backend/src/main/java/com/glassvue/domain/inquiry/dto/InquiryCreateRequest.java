package com.glassvue.domain.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record InquiryCreateRequest(

        @Schema(description = "제목", example = "배송 문의")
        @NotBlank @Size(max = 200)
        String title,

        @Schema(description = "문의 내용", example = "언제 배송되나요?")
        @NotBlank @Size(max = 2000)
        String content,

        @Schema(description = "비밀글 여부(작성자·관리자만 열람)", example = "false")
        boolean secret,

        @Schema(description = "첨부 이미지 id 목록(업로드 후 받은 id). 없으면 빈 배열/생략")
        @Size(max = 5, message = "문의 이미지는 최대 5장입니다")
        List<UUID> imageIds
) {
}
