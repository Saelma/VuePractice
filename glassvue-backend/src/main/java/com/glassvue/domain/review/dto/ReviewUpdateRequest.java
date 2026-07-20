package com.glassvue.domain.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record ReviewUpdateRequest(

        @Schema(description = "별점(1~5)", example = "4")
        @Min(1) @Max(5)
        int rating,

        @Schema(description = "리뷰 내용", example = "쓰다 보니 아쉬운 점도 있네요.")
        @NotBlank @Size(max = 2000)
        String content,

        @Schema(description = "포토 리뷰 이미지 id 목록. 수정 시 **전달한 목록으로 통째 교체**(빈 배열=이미지 제거)")
        @Size(max = 5, message = "리뷰 이미지는 최대 5장입니다")
        List<UUID> imageIds
) {
}
