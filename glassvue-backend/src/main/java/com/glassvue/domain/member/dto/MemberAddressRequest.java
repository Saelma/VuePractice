package com.glassvue.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 주소록 항목 등록·수정 요청.
 *
 * <p>{@link ShippingAddressRequest} 와 필드가 겹치지만 합치지 않는다 — 이쪽은 <b>별칭</b>과
 * <b>기본 배송지 지정</b>이 있고, 저쪽은 "기본 배송지 하나를 저장" 하는 옛 계약이라 의미가 다르다.
 * 길이 제한은 member_address 컬럼(V18)과 맞춘 값이다.
 */
public record MemberAddressRequest(

        @Schema(description = "별칭(집·회사 등)", example = "집")
        @NotBlank @Size(max = 30)
        String alias,

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

        @Schema(description = "이 주소를 기본 배송지로 지정할지. 첫 주소는 지정 여부와 무관하게 기본이 된다",
                example = "true")
        boolean setDefault
) {
}
