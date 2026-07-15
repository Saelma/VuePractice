package kr.co.ecstel.esp.domain.notice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NoticeCreateRequest(

        @Schema(description = "제목", example = "7월 사내 공지")
        @NotBlank @Size(max = 200)
        String title,

        @Schema(description = "본문", example = "공지 내용입니다.")
        @NotBlank
        String content,

        @Schema(description = "작성자", example = "홍길동")
        @NotBlank @Size(max = 50)
        String author,

        @Schema(description = "상단 고정 여부", example = "false")
        boolean pinned
) {
}
