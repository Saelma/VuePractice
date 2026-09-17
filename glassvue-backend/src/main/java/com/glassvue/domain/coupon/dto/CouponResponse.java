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
        // 가입 즉시 자동 발급되는 쿠폰인가(V36). 관리자 목록의 「가입 쿠폰」 배지가 이 값을 본다.
        boolean welcome,
        // 이벤트 발급 마감(V49). null 이면 상시 쿠폰 — 관리자 목록의 「이벤트」 배지가 이 값을 본다.
        Instant issueUntil,
        Instant createdAt,
        // 사용 기간이 끝났나(응답 시각 기준, 2026-09-17). 관리자 목록이 «가입 쿠폰인데 만료» 를 알리고
        // 「가입 쿠폰으로」 버튼을 감추는 근거다 — 화면이 시계로 다시 판정하지 않게 서버가 준다.
        boolean expired
) {
    public static CouponResponse from(Coupon c) {
        return new CouponResponse(
                c.getId(), c.getName(), c.getDiscountType(), c.getDiscountValue(),
                c.getMinOrderAmount(), c.getMaxDiscountAmount(),
                c.getValidFrom(), c.getValidUntil(), c.isWelcome(), c.getIssueUntil(), c.getCreatedAt(),
                c.isExpiredAt(Instant.now()));
    }
}
