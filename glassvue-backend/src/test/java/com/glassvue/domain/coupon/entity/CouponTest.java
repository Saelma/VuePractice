package com.glassvue.domain.coupon.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 쿠폰 할인 계산·유효성. 돈 계산이라 경계값을 못박는다. */
class CouponTest {

    private static final Instant NOW = Instant.parse("2026-07-23T12:00:00Z");

    private Coupon fixed(long value, long minOrder) {
        return Coupon.builder().name("정액").discountType(DiscountType.FIXED)
                .discountValue(value).minOrderAmount(minOrder)
                .validFrom(NOW.minus(1, ChronoUnit.DAYS)).validUntil(NOW.plus(1, ChronoUnit.DAYS)).build();
    }

    private Coupon percent(long rate, Long cap) {
        return Coupon.builder().name("정률").discountType(DiscountType.PERCENT)
                .discountValue(rate).minOrderAmount(0).maxDiscountAmount(cap)
                .validFrom(NOW.minus(1, ChronoUnit.DAYS)).validUntil(NOW.plus(1, ChronoUnit.DAYS)).build();
    }

    @Test
    @DisplayName("정액: 그 금액만큼 깎는다")
    void fixedDiscount() {
        assertThat(fixed(5_000, 0).discountFor(30_000)).isEqualTo(5_000);
    }

    @Test
    @DisplayName("정액: 상품합계를 넘지 않는다 — 넘으면 결제 금액이 음수가 된다")
    void fixedNeverExceedsTotal() {
        assertThat(fixed(50_000, 0).discountFor(30_000)).isEqualTo(30_000);
    }

    @Test
    @DisplayName("정률: 비율만큼 깎고, 상한이 있으면 거기서 멈춘다")
    void percentDiscount() {
        assertThat(percent(20, null).discountFor(50_000)).isEqualTo(10_000);
        assertThat(percent(20, 5_000L).discountFor(50_000)).isEqualTo(5_000); // 10,000 → 상한 5,000
    }

    @Test
    @DisplayName("🔴 버려지는 몫(Q-6): 정액이 상품합계보다 크면 그 차이가 사라진다")
    void forfeit_fixedOverTotal() {
        assertThat(fixed(5_000, 1_000).forfeitFor(1_000)).isEqualTo(4_000);
        assertThat(fixed(5_000, 1_000).forfeitFor(4_999)).isEqualTo(1);   // 경계 바로 아래
        assertThat(fixed(5_000, 1_000).forfeitFor(5_000)).isZero();       // 딱 맞으면 안 버린다
        assertThat(fixed(5_000, 1_000).forfeitFor(30_000)).isZero();
    }

    @Test
    @DisplayName("버려지는 몫: 정률 상한은 «잘린 것» 이 아니다 — 쿠폰의 규칙이다")
    void forfeit_percentCapIsNotForfeit() {
        assertThat(percent(20, 5_000L).forfeitFor(50_000)).isZero();   // 10,000 → 상한 5,000 이어도 0
        assertThat(percent(100, null).forfeitFor(7_000)).isZero();     // 전액 할인도 잘린 게 없다
        assertThat(fixed(5_000, 0).forfeitFor(0)).isZero();            // 빈 장바구니
    }

    @Test
    @DisplayName("빈 장바구니(0원)엔 할인이 없다")
    void noDiscountForEmpty() {
        assertThat(fixed(5_000, 0).discountFor(0)).isZero();
        assertThat(percent(20, null).discountFor(0)).isZero();
    }

    @Test
    @DisplayName("최소 주문금액 — 경계값(정확히 같으면 사용 가능)")
    void minOrder() {
        Coupon c = fixed(5_000, 30_000);
        assertThat(c.meetsMinOrder(29_999)).isFalse();
        assertThat(c.meetsMinOrder(30_000)).isTrue();   // '이상'이 기준
    }

    @Test
    @DisplayName("유효기간 — 시작·종료 시각 당일도 유효하다")
    void validPeriod() {
        Coupon c = fixed(5_000, 0);
        assertThat(c.isValidAt(NOW)).isTrue();
        assertThat(c.isValidAt(NOW.minus(1, ChronoUnit.DAYS))).isTrue();   // 시작 시각 = 유효
        assertThat(c.isValidAt(NOW.plus(1, ChronoUnit.DAYS))).isTrue();    // 종료 시각 = 유효
        assertThat(c.isValidAt(NOW.minus(2, ChronoUnit.DAYS))).isFalse();
        assertThat(c.isValidAt(NOW.plus(2, ChronoUnit.DAYS))).isFalse();
    }
}
