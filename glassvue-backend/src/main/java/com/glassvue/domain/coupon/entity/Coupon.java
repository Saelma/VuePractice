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

    /**
     * 이벤트 쿠폰의 <b>발급 마감</b> 시각(G-8, V49). <b>null 이면 이벤트가 아닌 상시 쿠폰</b>이다 —
     * 별도의 boolean 플래그를 두지 않는 이유가 여기 있다: 발급 마감일이 곧 «이벤트인가» 의 정의라
     * 플래그를 따로 두면 «켜졌는데 날짜가 없다» 는 모순 상태가 생긴다({@code welcome} 과 다른 점).
     *
     * <p>🔴 <b>발급 창과 사용 기간은 다른 것이다.</b> 발급은 {@code validFrom} ~ {@code issueUntil}
     * (보통 하루), 사용은 {@code validFrom} ~ {@code validUntil}(보통 한 달). 이 둘을 한 값으로 쓰면
     * «그 날 하루» 이벤트 쿠폰이 <b>그 날 자정에 만료돼 받자마자 못 쓴다</b>(V49 주석).
     */
    @Column(name = "issue_until")
    private Instant issueUntil;

    /**
     * 가입 즉시 자동 발급되는 쿠폰인가(G-2 후속, V36). <b>전체에서 한 장만</b> true —
     * 함수기반 유니크 인덱스(`ux_coupon_welcome`)가 DB 에서 보장한다.
     *
     * <p>설정(.env)이 아니라 여기 두는 이유: 쿠폰을 바꿀 때마다 **재시작**해야 했고,
     * 무엇이 가입 쿠폰인지 **화면에서 안 보였다**. 쿠폰이 데이터니 지정도 데이터다.
     */
    @Column(nullable = false)
    private boolean welcome;

    @Builder
    private Coupon(String name, DiscountType discountType, long discountValue,
                   long minOrderAmount, Long maxDiscountAmount, Instant validFrom, Instant validUntil,
                   Instant issueUntil) {
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.maxDiscountAmount = maxDiscountAmount;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.issueUntil = issueUntil;
    }

    /** 가입 쿠폰으로 지정/해제(관리자). "하나만" 규칙은 서비스가 기존 것을 해제해 지킨다. */
    public void markWelcome(boolean welcome) {
        this.welcome = welcome;
    }

    public boolean isValidAt(Instant at) {
        return !at.isBefore(validFrom) && !at.isAfter(validUntil);
    }

    /** 이벤트 쿠폰인가 — 발급 마감이 정해져 있으면 그렇다(G-8). */
    public boolean isEventCoupon() {
        return issueUntil != null;
    }

    /**
     * 지금 「받기」로 발급받을 수 있는가.
     *
     * <p>⚠ {@link #isValidAt} 과 <b>다른 질문</b>이다 — 저건 «쓸 수 있나», 이건 «받을 수 있나».
     * 발급 창이 닫혀도 이미 받은 쿠폰은 {@code validUntil} 까지 멀쩡히 쓸 수 있다.
     */
    public boolean isIssuableAt(Instant at) {
        return isEventCoupon() && !at.isBefore(validFrom) && !at.isAfter(issueUntil);
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
