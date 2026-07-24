package com.glassvue.domain.catalog.dto;

import com.glassvue.domain.catalog.entity.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record ProductCreateRequest(

        @Schema(description = "상품명", example = "무선 키보드")
        @NotBlank @Size(max = 200)
        String name,

        @Schema(description = "상품 설명")
        @NotBlank
        String description,

        @Schema(description = "가격(원) — 실제 판매가", example = "31200")
        @NotNull @PositiveOrZero
        Long price,

        @Schema(description = "정가(할인 전). 비우면 할인 없음. 판매가보다 커야 의미가 있다", example = "39000")
        @PositiveOrZero
        Long listPrice,

        @Schema(description = "상태(없으면 SELLING)", example = "SELLING")
        ProductStatus status,

        @Schema(description = "카테고리 id")
        @NotNull
        UUID categoryId,

        @Schema(description = "업로드한 이미지 id 목록(순서대로)")
        List<UUID> imageIds,

        @Schema(description = "옵션 목록(최소 1개). 단일 옵션 상품이면 한 줄만 담는다")
        @jakarta.validation.constraints.NotEmpty
        @jakarta.validation.Valid
        List<VariantRequest> variants
) {
}
