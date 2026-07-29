package com.glassvue.global.policy.dto;

import com.glassvue.global.policy.ShippingPolicy;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 배송비 정책 공개 노출(2026-07-29). <b>화면이 정책 숫자를 갖지 않게</b> 하려고 만들었다.
 *
 * <p>장바구니·주문서는 서버가 계산한 {@code amountUntilFree} 를 받아 쓰면 되지만,
 * <b>홈의 비로그인 혜택 안내</b>는 장바구니가 없어 기준 금액 자체가 필요하다. 그 값을 화면에
 * 하드코딩하면 {@code application.yml} 을 바꿨을 때 <b>안내 문구만 조용히 거짓말</b>이 된다
 * (적립금 패널이 "다음 등급까지 남은 금액"을 서버에서 받는 것과 같은 판단).
 */
@Schema(description = "배송비 정책")
public record ShippingPolicyResponse(

        @Schema(description = "기본 배송비(원)", example = "3000")
        long fee,

        @Schema(description = "이 금액 이상이면 무료배송. 0이면 무료배송 없음", example = "30000")
        long freeThreshold
) {
    public static ShippingPolicyResponse from(ShippingPolicy policy) {
        return new ShippingPolicyResponse(policy.getFee(), policy.getFreeThreshold());
    }
}
