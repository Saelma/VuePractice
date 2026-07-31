package com.glassvue.domain.coupon.repository;

import com.glassvue.domain.coupon.entity.Coupon;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    /**
     * 가입 즉시 자동 발급되는 쿠폰(G-2 후속, V36). 지정된 게 없으면 비어 있다 = <b>기능 꺼짐</b>.
     *
     * <p>{@code Optional} 인 근거는 규약이 아니라 <b>제약</b>이다 — 함수기반 유니크 인덱스
     * {@code ux_coupon_welcome} 이 {@code welcome=1} 인 행을 하나로 막는다(V36 주석).
     */
    Optional<Coupon> findByWelcomeTrue();
}
