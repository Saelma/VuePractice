package com.glassvue.domain.coupon.repository;

import com.glassvue.domain.coupon.entity.MemberCoupon;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberCouponRepository extends JpaRepository<MemberCoupon, UUID> {

    /**
     * 내 쿠폰 목록 — 미사용만, 발급 최신순.
     *
     * <p>{@code coupon} 을 fetch join 한다: 목록이 쿠폰 이름·할인값을 전부 보여주므로
     * 안 하면 건마다 쿠폰을 다시 읽는 N+1 이 된다(2026-07-23 에 문의 목록에서 실측한 그 문제).
     */
    @Query("select mc from MemberCoupon mc join fetch mc.coupon "
            + "where mc.memberId = :memberId and mc.usedAt is null order by mc.createdAt desc")
    List<MemberCoupon> findUnusedByMember(@Param("memberId") UUID memberId);

    /** 회원 삭제 정리용(F-1). 지우는 것은 <b>발급분</b>이고 쿠폰 정의({@code coupon})는 남는다. */
    long deleteByMemberId(UUID memberId);

    /**
     * 이미 받았나 — 「받기」 버튼을 「받음」으로 그릴지, 관리자 발급을 거절할지의 근거(G-8, V49).
     *
     * <p>⚠ <b>이 확인만으로는 중복이 안 막힌다.</b> 두 요청이 같은 순간 «없다» 를 읽으면 둘 다 발급된다 —
     * 최종 방어는 유니크 인덱스 {@code ux_member_coupon_once}(V49)다. 여기서 미리 보는 이유는
     * 흔한 경우에 <b>제약 위반 500 대신 뜻이 있는 4xx</b> 를 주기 위해서다.
     */
    boolean existsByMemberIdAndCouponId(UUID memberId, UUID couponId);
}
