package com.glassvue.domain.catalog.dto;

import com.glassvue.domain.catalog.entity.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ProductCreateRequest(

        @Schema(description = "상품명", example = "무선 키보드")
        @NotBlank @Size(max = 200)
        String name,

        @Schema(description = "상품 설명")
        @NotBlank
        String description,

        @Schema(description = "가격(원)", example = "39000")
        @NotNull @PositiveOrZero
        Long price,

        @Schema(description = "재고 수량", example = "100")
        @NotNull @PositiveOrZero
        Long stock,

        @Schema(description = "상태(없으면 SELLING)", example = "SELLING")
        ProductStatus status,

        @Schema(description = "카테고리 id")
        @NotNull
        UUID categoryId
) {
}
