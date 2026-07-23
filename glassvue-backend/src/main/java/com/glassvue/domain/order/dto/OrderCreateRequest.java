package com.glassvue.domain.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 주문 생성 요청 — 배송지.
 *
 * <p>상품·수량은 장바구니에서 가져오므로 요청에 담지 않는다(클라이언트가 가격을 조작할 여지를 주지 않는다).
 * 배송지만 받고, 저장은 {@code Order}에 스냅샷으로 들어간다.
 */
public record OrderCreateRequest(

        @Schema(description = "수령인", example = "홍길동")
        @NotBlank @Size(max = 50)
        String recipient,

        @Schema(description = "연락처", example = "010-1234-5678")
        @NotBlank @Size(max = 20)
        String phone,

        @Schema(description = "우편번호", example = "06134")
        @NotBlank @Size(max = 10)
        String zipcode,

        @Schema(description = "기본 주소", example = "서울시 강남구 테헤란로 1")
        @NotBlank @Size(max = 200)
        String address1,

        @Schema(description = "상세 주소(선택)", example = "3층 301호")
        @Size(max = 200)
        String address2,

        @Schema(description = "사용할 쿠폰(내 쿠폰 id). 비우면 쿠폰 미사용")
        java.util.UUID memberCouponId
) {
}
