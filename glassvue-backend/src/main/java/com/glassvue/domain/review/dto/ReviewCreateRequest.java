package com.glassvue.domain.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewCreateRequest(

        @Schema(description = "별점(1~5)", example = "5")
        @Min(1) @Max(5)
        int rating,

        @Schema(description = "리뷰 내용", example = "타건감이 좋아요.")
        @NotBlank @Size(max = 2000)
        String content
) {
}
