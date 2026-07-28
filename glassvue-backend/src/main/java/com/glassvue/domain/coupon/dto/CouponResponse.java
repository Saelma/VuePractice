package com.glassvue.domain.coupon.dto;

import com.glassvue.domain.coupon.entity.Coupon;
import com.glassvue.domain.coupon.entity.DiscountType;
import java.time.Instant;
import java.util.UUID;

/**
 * 쿠폰 정의(관리자 목록용). 회원별 발급분({@link MemberCouponResponse})이 아니라 쿠폰 그 자체다 —
 * 할인 규칙·유효기간을 그대로 노출해 관리자가 무엇을 만들었는지 본다.
 */
public record CouponResponse(
        UUID id,
        String name,
        DiscountType discountType,
        long discountValue,
        long minOrderAmount,
        Long maxDiscountAmount,
        Instant validFrom,
        Instant validUntil,
        Instant createdAt
) {
    public static CouponResponse from(Coupon c) {
        return new CouponResponse(
                c.getId(), c.getName(), c.getDiscountType(), c.getDiscountValue(),
                c.getMinOrderAmount(), c.getMaxDiscountAmount(),
                c.getValidFrom(), c.getValidUntil(), c.getCreatedAt());
    }
}
