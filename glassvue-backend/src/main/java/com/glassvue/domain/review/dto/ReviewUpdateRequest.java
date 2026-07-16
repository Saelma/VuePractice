package com.glassvue.domain.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewUpdateRequest(

        @Schema(description = "별점(1~5)", example = "4")
        @Min(1) @Max(5)
        int rating,

        @Schema(description = "리뷰 내용", example = "쓰다 보니 아쉬운 점도 있네요.")
        @NotBlank @Size(max = 2000)
        String content
) {
}
