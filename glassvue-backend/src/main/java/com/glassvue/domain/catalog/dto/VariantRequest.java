package com.glassvue.domain.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 상품 옵션 등록·수정 요청 한 줄. 상품 생성/수정 요청 안에 목록으로 들어간다.
 *
 * <p>단일 옵션 상품(과자 등)은 이 목록에 <b>한 줄만</b> 담으면 된다(이름 "기본" 등). 화면은 옵션이
 * 2개 이상일 때만 선택 UI 를 보여준다. 가격차는 음수도 허용(할인 옵션)이라 {@code @PositiveOrZero} 를 안 건다.
 */
public record VariantRequest(

        @Schema(description = "옵션명", example = "검정 / M")
        @NotBlank @Size(max = 100)
        String name,

        @Schema(description = "기본가 대비 가격차(원). 음수 가능", example = "2000")
        @NotNull
        Long priceDelta,

        @Schema(description = "재고 수량", example = "50")
        @NotNull @PositiveOrZero
        Long stock
) {
}
