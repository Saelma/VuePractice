package com.glassvue.domain.catalog.dto;

import com.glassvue.domain.catalog.entity.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ProductUpdateRequest(

        @NotBlank @Size(max = 200)
        String name,

        @NotBlank
        String description,

        @NotNull @PositiveOrZero
        Long price,

        @NotNull @PositiveOrZero
        Long stock,

        @NotNull
        ProductStatus status,

        @NotNull
        UUID categoryId
) {
}
