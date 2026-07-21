package com.glassvue.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 기본 배송지 저장(마이페이지). 주문서에 자동으로 채워 넣기 위한 값이다. */
public record ShippingAddressRequest(

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
        String address2
) {
}
