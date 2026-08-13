package com.glassvue.domain.coupon.repository;

import com.glassvue.domain.coupon.entity.Coupon;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    /**
     * 가입 즉시 자동 발급되는 쿠폰(G-2 후속, V36). 지정된 게 없으면 비어 있다 = <b>기능 꺼짐</b>.
     *
     * <p>{@code Optional} 인 근거는 규약이 아니라 <b>제약</b>이다 — 함수기반 유니크 인덱스
     * {@code ux_coupon_welcome} 이 {@code welcome=1} 인 행을 하나로 막는다(V36 주석).
     */
    Optional<Coupon> findByWelcomeTrue();

    /**
     * 지금 발급 창이 열려 있는 이벤트 쿠폰(G-8, V49).
     *
     * <p>{@code Optional} 인 근거는 <b>등록 시 겹침 검사</b>다 — 발급 창이 겹치는 이벤트는 아예 등록되지
     * 않으므로(`CouponService.create`) 어느 순간에도 열린 것은 많아야 하나다.
     * ⚠ 이 보장은 <b>DB 제약이 아니라 앱 검사</b>다(Oracle 유니크 인덱스로는 기간 겹침을 못 막는다).
     * 그래서 «겹치는 둘을 동시에 등록» 을 테스트로 못 박아 둔다.
     */
    @Query("select c from Coupon c where c.issueUntil is not null "
            + "and c.validFrom <= :at and c.issueUntil >= :at")
    Optional<Coupon> findIssuableAt(@Param("at") Instant at);

    /**
     * 아직 시작하지 않은 이벤트 중 <b>가장 가까운 것</b> — 배너의 «다음 이벤트 D-3» 예고에 쓴다.
     *
     * <p>⚠ 하나만 필요하지만 {@code Optional} 로 받지 않는다 — 미래 이벤트는 여러 개일 수 있고
     * (겹침 금지는 <b>발급 창</b>에만 걸린다), «없으면 비어 있음» 은 호출부가 리스트로 판단한다.
     */
    @Query("select c from Coupon c where c.issueUntil is not null and c.validFrom > :at "
            + "order by c.validFrom asc")
    List<Coupon> findUpcomingEvents(@Param("at") Instant at, Limit limit);

    /**
     * 발급 창이 겹치는 기존 이벤트 — 등록을 거부할 근거다.
     *
     * <p>🔴 <b>겹침의 대상은 발급 창({@code validFrom} ~ {@code issueUntil})이지 사용 기간이 아니다.</b>
     * 8/15 쿠폰과 8/20 쿠폰의 사용 기간은 9월 중순까지 나란히 살아 있는 게 정상이라,
     * 여기를 헷갈리면 <b>두 번째 이벤트부터 등록이 통째로 막힌다</b>(BACKLOG G-8).
     */
    @Query("select c from Coupon c where c.issueUntil is not null "
            + "and c.validFrom <= :issueUntil and c.issueUntil >= :validFrom")
    List<Coupon> findEventsOverlapping(@Param("validFrom") Instant validFrom,
                                       @Param("issueUntil") Instant issueUntil);
}
