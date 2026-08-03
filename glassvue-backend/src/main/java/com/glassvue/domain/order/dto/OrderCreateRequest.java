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

        /*
         * 배송 요청사항(선택, B-20 2026-08-03). 주문 시점 스냅샷으로 orders.ship_memo 에 남는다.
         * ⚠ 200자 상한은 DB 컬럼(VARCHAR2(200 CHAR))과 맞춘 값이다 — 여기가 더 크면
         *   ORA-12899 로 주문 자체가 실패한다(V12 닉네임 스냅샷 사고와 같은 자리).
         */
        @Schema(description = "배송 요청사항(선택)", example = "부재 시 경비실에 맡겨 주세요")
        @Size(max = 200)
        String shipMemo,

        @Schema(description = "사용할 쿠폰(내 쿠폰 id). 비우면 쿠폰 미사용")
        java.util.UUID memberCouponId,

        /**
         * 사용할 적립금. 비우거나 0이면 미사용.
         *
         * <p>금액을 **클라이언트가 정한다**는 점에서 쿠폰(id만 받고 할인액은 서버가 계산)과 다르다 —
         * 적립금은 "얼마 쓸지"가 본질적으로 사용자 선택이기 때문이다. 대신 서버가
         * <b>잔액</b>과 <b>상품합계 − 쿠폰할인 상한</b> 둘 다로 검증한다(위조하면 거절).
         */
        @Schema(description = "사용할 적립금(원). 비우면 미사용", example = "1000")
        Long usePoint
) {
}
