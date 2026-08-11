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

    /**
     * 사용을 <b>되돌린다</b> — 주문 취소·반품 승인 때 (2026-08-11).
     *
     * <p>⚠ 이 메서드가 없어서 <b>취소해도 쿠폰이 안 돌아왔다.</b> {@code use()} 만 있고 짝이 없던 자리다
     * — 「되돌리는 것들」(재고·적립금·판매량)이 한 줄로 모여 있지 않으면 하나씩 빠진다는 것의 실례다
     * (2026-08-07 에 적립금이 같은 이유로 빠져 있었다).
     *
     * <p>⚠ <b>이미 미사용이면 아무 일도 하지 않고 {@code false} 를 준다.</b> 호출부가 «실제로 되돌렸나»
     * 를 알아야 로그·집계가 일어나지 않은 일을 적지 않는다(리뷰·문의 숨김의 {@code setHidden} 과 같은 규약).
     * 같은 주문을 두 번 취소하는 경로는 없지만, 없다는 이유로 조용히 두 번 되돌리게 두지 않는다.
     *
     * <p>⚠ <b>유효기간은 보지 않는다.</b> 되돌린 쿠폰이 이미 만료됐을 수 있는데, 그건 되돌리기의 문제가
     * 아니라 쿠폰 자체의 문제다 — 만료된 채 돌아온 쿠폰은 다음 {@code redeem} 에서 COUPON_EXPIRED 로
     * 걸린다. 여기서 «만료됐으니 안 돌려준다» 로 판단하면 <b>고객은 쓴 적도 없는 쿠폰을 잃는다.</b>
     */
    public boolean restore() {
        if (usedAt == null) {
            return false;
        }
        this.usedAt = null;
        return true;
    }

    public void use() {
        this.usedAt = Instant.now();
    }
}
