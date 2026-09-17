package com.glassvue.domain.coupon.dto;

import com.glassvue.domain.coupon.entity.MemberCoupon;
import java.time.Instant;
import java.util.UUID;

/**
 * 쿠폰 한 종류의 발급분 한 장 — 관리자 보유자 목록(Q-7).
 *
 * <p>{@code loginId} 는 member 도메인이 채운다({@code MemberService.loginIdsOf}). 쿠폰 도메인은 회원 테이블을
 * 안 읽는다. ⚠ {@code usedAt} 이 있으면 <b>회수할 수 없다</b> — 화면은 그 줄에 회수 버튼을 안 그린다.
 */
public record IssuedCouponResponse(
        UUID id,
        UUID memberId,
        String loginId,
        Instant issuedAt,
        Instant usedAt
) {
    public static IssuedCouponResponse of(MemberCoupon mc, String loginId) {
        return new IssuedCouponResponse(mc.getId(), mc.getMemberId(), loginId, mc.getCreatedAt(), mc.getUsedAt());
    }
}
