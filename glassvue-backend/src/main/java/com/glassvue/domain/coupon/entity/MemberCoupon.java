package com.glassvue.domain.coupon.entity;

import com.glassvue.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 회원이 발급받은 쿠폰. 사용하면 시각을 남긴다(결제·발송·취소와 같은 방식). */
@Entity
@Getter
@Table(name = "member_coupon")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberCoupon extends BaseTimeEntity {

    /** member를 직접 참조하지 않는다(도메인 경계) — 느슨한 UUID. */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "member_id", columnDefinition = "RAW(16)", nullable = false, updatable = false)
    private UUID memberId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id", columnDefinition = "RAW(16)", nullable = false)
    private Coupon coupon;

    /** null이면 미사용. 사용 시각을 남겨 "언제 썼는지"가 CS에서 확인된다. */
    @Column(name = "used_at")
    private Instant usedAt;

    private MemberCoupon(UUID memberId, Coupon coupon) {
        this.memberId = memberId;
        this.coupon = coupon;
    }

    public static MemberCoupon issue(UUID memberId, Coupon coupon) {
        return new MemberCoupon(memberId, coupon);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isOwnedBy(UUID memberId) {
        return this.memberId.equals(memberId);
    }

    public void use() {
        this.usedAt = Instant.now();
    }
}
