package com.glassvue.domain.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record CartItemAddRequest(

        @Schema(description = "옵션(variant) id — 담을 옵션")
        @NotNull
        UUID variantId,

        @Schema(description = "담을 수량", example = "1")
        @NotNull @Positive
        Long quantity
) {
}
