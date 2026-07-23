package com.glassvue.global.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 배송비 계산 규칙. 정책은 설정으로 바뀌지만 계산 자체는 여기 고정된다. */
class ShippingPolicyTest {

    private ShippingPolicy policy(long fee, long threshold) {
        ShippingPolicy p = new ShippingPolicy();
        p.setFee(fee);
        p.setFreeThreshold(threshold);
        return p;
    }

    @Test
    @DisplayName("기준 미만이면 배송비를 받는다")
    void chargesBelowThreshold() {
        ShippingPolicy p = policy(3000, 30000);
        assertThat(p.feeFor(29_999)).isEqualTo(3000);
        assertThat(p.feeFor(1)).isEqualTo(3000);
    }

    @Test
    @DisplayName("기준 이상이면 무료 — 경계값(정확히 기준)도 무료다")
    void freeAtOrAboveThreshold() {
        ShippingPolicy p = policy(3000, 30000);
        assertThat(p.feeFor(30_000)).isZero();   // 경계: '이상'이므로 무료
        assertThat(p.feeFor(30_001)).isZero();
    }

    @Test
    @DisplayName("빈 장바구니(0원)에는 배송비를 붙이지 않는다")
    void emptyCartHasNoFee() {
        // 붙이면 화면에 "0원 + 배송비 3,000원"이 떠서 담지도 않은 값을 청구하는 것처럼 보인다.
        ShippingPolicy p = policy(3000, 30000);
        assertThat(p.feeFor(0)).isZero();
        assertThat(p.feeFor(-1)).isZero();
    }

    @Test
    @DisplayName("무료 기준이 0이면 무료배송 없음 — 항상 배송비를 받는다")
    void zeroThresholdMeansNoFreeShipping() {
        ShippingPolicy p = policy(3000, 0);
        assertThat(p.feeFor(1_000_000)).isEqualTo(3000);
    }

    @Test
    @DisplayName("무료배송까지 남은 금액 — 이미 무료면 0")
    void amountUntilFree() {
        ShippingPolicy p = policy(3000, 30000);
        assertThat(p.amountUntilFree(20_000)).isEqualTo(10_000);
        assertThat(p.amountUntilFree(30_000)).isZero();
        assertThat(p.amountUntilFree(0)).isZero();          // 빈 장바구니에 "N원 더" 안내는 안 띄운다
        assertThat(policy(3000, 0).amountUntilFree(10)).isZero(); // 무료배송 정책 자체가 없음
    }
}
