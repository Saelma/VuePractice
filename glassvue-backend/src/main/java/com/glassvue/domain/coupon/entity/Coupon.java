package com.glassvue.domain.coupon.entity;

import com.glassvue.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 쿠폰 정의(마스터). 회원이 발급받은 것은 {@link MemberCoupon} 이다. */
@Entity
@Getter
@Table(name = "coupon")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon extends BaseTimeEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false)
    private long discountValue;

    /** 최소 주문금액(상품합계 기준). 0이면 제한 없음. */
    @Column(name = "min_order_amount", nullable = false)
    private long minOrderAmount;

    /** 정률 할인의 상한(원). null이면 상한 없음. 정액에는 의미 없다. */
    @Column(name = "max_discount_amount")
    private Long maxDiscountAmount;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    @Builder
    private Coupon(String name, DiscountType discountType, long discountValue,
                   long minOrderAmount, Long maxDiscountAmount, Instant validFrom, Instant validUntil) {
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.maxDiscountAmount = maxDiscountAmount;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
    }

    public boolean isValidAt(Instant at) {
        return !at.isBefore(validFrom) && !at.isAfter(validUntil);
    }

    public boolean meetsMinOrder(long itemsTotal) {
        return itemsTotal >= minOrderAmount;
    }

    /**
     * 상품합계에 대한 할인액을 계산한다.
     *
     * <p><b>상품합계를 넘지 않는다</b> — 넘으면 결제 금액이 음수가 되고, 그러면 배송비만 남거나
     * 환불해야 하는 이상한 상태가 된다. 정률은 {@code maxDiscountAmount} 상한도 함께 적용한다.
     *
     * <p>여기 넘어오는 값은 <b>할인 전 상품합계</b>다. 배송비는 이 계산에 들어오지 않는다 —
     * 무료배송 기준도 할인 전 금액으로 판단하기 때문이다(2026-07-23 결정).
     */
    public long discountFor(long itemsTotal) {
        if (itemsTotal <= 0) {
            return 0;
        }
        long raw = (discountType == DiscountType.FIXED)
                ? discountValue
                : itemsTotal * discountValue / 100;
        if (discountType == DiscountType.PERCENT && maxDiscountAmount != null) {
            raw = Math.min(raw, maxDiscountAmount);
        }
        return Math.min(raw, itemsTotal);
    }
}
