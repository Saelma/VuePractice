package com.glassvue.domain.coupon.dto;

import com.glassvue.domain.coupon.entity.Coupon;
import com.glassvue.domain.coupon.entity.MemberCoupon;
import com.glassvue.domain.coupon.entity.DiscountType;
import java.time.Instant;
import java.util.UUID;

/**
 * 내 쿠폰 한 장.
 *
 * <p>{@code discountPreview} 는 <b>지금 장바구니 기준으로 얼마 깎이는지</b>다 —
 * "10% 할인"만 보여주면 고객이 직접 계산해야 하고, 최소주문금액을 못 채웠는지도 알 수 없다.
 * {@code usable} 이 false 면 이유({@code reason})를 함께 준다.
 */
public record MemberCouponResponse(
        UUID id,
        String name,
        DiscountType discountType,
        long discountValue,
        long minOrderAmount,
        Long maxDiscountAmount,
        Instant validUntil,
        long discountPreview,
        boolean usable,
        String reason
) {
    public static MemberCouponResponse of(MemberCoupon mc, long itemsTotal, Instant now) {
        Coupon c = mc.getCoupon();
        boolean inPeriod = c.isValidAt(now);
        boolean minOk = c.meetsMinOrder(itemsTotal);
        String reason = !inPeriod ? "사용 기간이 아닙니다"
                : !minOk ? String.format("%,d원 이상 주문 시 사용 가능", c.getMinOrderAmount())
                : null;
        return new MemberCouponResponse(
                mc.getId(), c.getName(), c.getDiscountType(), c.getDiscountValue(),
                c.getMinOrderAmount(), c.getMaxDiscountAmount(), c.getValidUntil(),
                (inPeriod && minOk) ? c.discountFor(itemsTotal) : 0,
                inPeriod && minOk, reason);
    }
}
