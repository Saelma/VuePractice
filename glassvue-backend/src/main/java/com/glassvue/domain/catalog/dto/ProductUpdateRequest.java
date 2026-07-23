package com.glassvue.domain.catalog.dto;

import com.glassvue.domain.catalog.entity.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record ProductUpdateRequest(

        @NotBlank @Size(max = 200)
        String name,

        @NotBlank
        String description,

        @NotNull @PositiveOrZero
        Long price,

        /** 정가(할인 전). 비우면 할인 없음 — 판매가보다 커야 의미가 있다. */
        @PositiveOrZero
        Long listPrice,

        @NotNull @PositiveOrZero
        Long stock,

        @NotNull
        ProductStatus status,

        @NotNull
        UUID categoryId,

        @Schema(description = "이미지 id 목록(순서대로, 교체)")
        List<UUID> imageIds
) {
}
