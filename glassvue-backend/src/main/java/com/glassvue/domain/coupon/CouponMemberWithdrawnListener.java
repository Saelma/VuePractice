package com.glassvue.domain.coupon;

import com.glassvue.domain.member.event.MemberWithdrawnEvent;
import com.glassvue.domain.coupon.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 회원이 사라지면 발급된 쿠폰을 지운다(쿠폰 정의는 남는다) — member 도메인의 {@link MemberWithdrawnEvent}를 받아 잇는 <b>어댑터</b>다(위임만 한다).
 *
 * <p>기본 {@code @EventListener} 라 발행측 트랜잭션에 합류한다 — 정리가 실패하면 회원 삭제도 롤백된다.
 * 로직은 두지 않는다(진짜 주체는 {@link CouponService }).
 */
@Component
@RequiredArgsConstructor
public class CouponMemberWithdrawnListener {

    private final CouponService couponService;

    @EventListener
    public void on(MemberWithdrawnEvent event) {
        couponService.deleteAllForMember(event.memberId());
    }
}
