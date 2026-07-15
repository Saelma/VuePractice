package com.glassvue.domain.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CartItemUpdateRequest(

        @Schema(description = "변경할 수량", example = "3")
        @NotNull @Positive
        Long quantity
) {
}
