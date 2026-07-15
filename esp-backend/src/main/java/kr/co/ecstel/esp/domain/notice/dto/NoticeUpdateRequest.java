package kr.co.ecstel.esp.domain.notice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NoticeUpdateRequest(

        @Schema(description = "제목", example = "수정된 제목")
        @NotBlank @Size(max = 200)
        String title,

        @Schema(description = "본문", example = "수정된 내용입니다.")
        @NotBlank
        String content,

        @Schema(description = "상단 고정 여부", example = "true")
        boolean pinned
) {
}
