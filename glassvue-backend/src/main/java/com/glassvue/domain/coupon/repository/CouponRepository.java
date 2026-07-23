package com.glassvue.domain.coupon.repository;

import com.glassvue.domain.coupon.entity.Coupon;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {
}
