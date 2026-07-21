package com.glassvue.domain.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record InquiryUpdateRequest(

        @Schema(description = "제목", example = "배송 문의(수정)")
        @NotBlank @Size(max = 200)
        String title,

        @Schema(description = "문의 내용", example = "내용을 수정합니다.")
        @NotBlank @Size(max = 2000)
        String content,

        @Schema(description = "비밀글 여부", example = "true")
        boolean secret,

        @Schema(description = "첨부 이미지 id 목록. 보낸 목록으로 통째 교체(빈 배열이면 제거)")
        @Size(max = 5, message = "문의 이미지는 최대 5장입니다")
        List<UUID> imageIds
) {
}
