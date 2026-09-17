package com.glassvue.domain.coupon.repository;

import com.glassvue.domain.coupon.entity.MemberCoupon;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /** 쿠폰 한 종류의 발급분 전부 — 관리자 보유자 목록(Q-7). 사용한 것도 함께, 발급 최신순. */
    List<MemberCoupon> findByCouponIdOrderByCreatedAtDesc(UUID couponId);

    /** 정의를 지워도 되나 — 발급분이 하나라도 있으면 FK({@code fk_member_coupon_coupon})가 막는다(Q-7). */
    boolean existsByCouponId(UUID couponId);

    /**
     * <b>미사용일 때만</b> 지운다 — 회수(Q-7). 지운 행 수(0 또는 1)를 준다.
     *
     * <p>🔴 <b>«읽고 확인한 뒤 지우기» 로 하지 않는 이유</b>: 관리자가 «미사용» 을 읽은 순간 고객이 그 쿠폰으로
     * 주문하면 둘 다 통과해 <b>주문이 가리키는 행이 사라진다</b>(취소 때 쿠폰 복구가 대상을 잃는다, V46).
     * 조건을 DELETE 문에 넣으면 Oracle 이 행 잠금을 기다린 뒤 조건을 <b>다시 평가</b>해 0 을 준다.
     * ⚠ 반대 순서(회수가 먼저 커밋)면 주문 쪽 {@code use()} 의 UPDATE 가 0행이 되어 주문이 실패한다 —
     * 이미 회수된 쿠폰이니 그게 맞는 결과다.
     */
    // ⚠ clearAutomatically — 벌크 DELETE 는 영속성 컨텍스트를 안 거친다. 안 비우면 같은 트랜잭션의
    //    findById 가 이미 지운 행을 캐시에서 돌려준다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from MemberCoupon mc where mc.id = :id and mc.usedAt is null")
    int deleteUnusedById(@Param("id") UUID id);
}
