package com.glassvue.domain.coupon.dto;

import com.glassvue.domain.coupon.entity.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** 쿠폰 생성(관리자). */
public record CouponCreateRequest(

        @Schema(description = "쿠폰명", example = "가입 축하 5,000원")
        @NotBlank @Size(max = 100)
        String name,

        @Schema(description = "할인 방식", example = "FIXED")
        @NotNull
        DiscountType discountType,

        @Schema(description = "할인값 — FIXED면 원, PERCENT면 %", example = "5000")
        @NotNull @Positive
        Long discountValue,

        @Schema(description = "최소 주문금액(상품합계 기준). 0이면 제한 없음", example = "30000")
        @PositiveOrZero
        Long minOrderAmount,

        @Schema(description = "정률 할인의 상한(원). 비우면 상한 없음. 정액에는 의미 없다", example = "10000")
        @PositiveOrZero
        Long maxDiscountAmount,

        @NotNull Instant validFrom,
        @NotNull Instant validUntil,

        /*
         * 이벤트 쿠폰의 발급 마감(G-8, V49). **비우면 상시 쿠폰**이다 — 지금까지 만들던 것과 같다.
         * ⚠ @NotNull 을 붙이지 않는 것이 이 기능의 «지정 안 함 = 꺼짐» 이다(G-2 와 같은 방식).
         */
        @Schema(description = "이벤트 발급 마감 시각. 비우면 상시 쿠폰", example = "2026-08-15T14:59:59Z")
        Instant issueUntil
) {
}
