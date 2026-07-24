package com.glassvue.domain.point.entity;

import com.glassvue.global.common.BaseTimeEntity;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 회원의 적립금 계정 (2026-07-24, 백로그 C-10).
 *
 * <p>잔액·등급을 {@code member} 테이블이 아니라 여기 두는 이유는 <b>도메인 경계</b>다 —
 * 그러면 point 도메인이 member 테이블을 만져야 한다. coupon 이 {@code member_coupon} 을 갖는 것과 같다.
 *
 * <p><b>잔액은 이력({@code point_history})의 합이어야 한다.</b> 이력이 원장이고 이 값은 캐시다.
 * 그래서 잔액을 바꾸는 경로는 {@code earn}·{@code use} 둘뿐이고, 서비스가 항상 이력을 함께 남긴다.
 */
@Entity
@Getter
@Table(name = "point_account")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointAccount extends BaseTimeEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "member_id", columnDefinition = "RAW(16)", nullable = false, updatable = false)
    private UUID memberId;

    @Column(nullable = false)
    private long balance;

    /** 등급 산정 기준 — 배송완료된 주문의 상품매출 누계. 매번 orders 를 합산하지 않으려고 비정규화했다. */
    @Column(name = "total_purchase", nullable = false)
    private long totalPurchase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberGrade grade;

    private PointAccount(UUID memberId) {
        this.memberId = memberId;
        this.balance = 0L;
        this.totalPurchase = 0L;
        this.grade = MemberGrade.BRONZE;
    }

    public static PointAccount openFor(UUID memberId) {
        return new PointAccount(memberId);
    }

    /**
     * 사용 — 잔액이 모자라면 거절한다.
     *
     * <p>DB 에도 {@code balance >= 0} CHECK 가 걸려 있다. 여기서 막는 게 정상 경로지만,
     * 동시 요청으로 검사와 차감 사이가 벌어지면 CHECK 가 최종 방어선이 된다.
     */
    public void use(long amount) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.POINT_INVALID_AMOUNT);
        }
        if (balance < amount) {
            throw new BusinessException(ErrorCode.POINT_NOT_ENOUGH);
        }
        this.balance -= amount;
    }

    public void earn(long amount) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.POINT_INVALID_AMOUNT);
        }
        this.balance += amount;
    }

    /**
     * 반품 환불 순변동을 잔액에 더한다(2026-07-24, C-9).
     *
     * <p>순변동 = 환불액 − 적립회수. 환불액(상품합계−쿠폰)이 적립(그 몇 %)보다 항상 크므로 ≥ 0 이지만,
     * 방어적으로 음수면 0 으로 막는다(잔액이 음수가 되면 DB CHECK 에 걸린다).
     */
    public void refund(long netAmount) {
        this.balance += Math.max(0L, netAmount);
    }

    /**
     * 반품으로 구매확정액을 되돌리고 등급을 다시 정한다 — 강등될 수 있다.
     * {@code addPurchase} 의 반대. 0 아래로는 안 내려간다.
     */
    public void subtractPurchase(long amount) {
        this.totalPurchase = Math.max(0L, this.totalPurchase - Math.max(0L, amount));
        this.grade = MemberGrade.of(this.totalPurchase);
    }

    /**
     * 구매확정액을 누적하고 등급을 다시 정한다.
     *
     * <p>등급을 별도 경로로 바꾸지 않는 이유: 누적액과 등급이 <b>따로 움직일 수 있으면</b>
     * 둘이 어긋난 상태가 생긴다. 항상 함께 바뀌도록 한 메서드로 묶었다.
     *
     * @return 등급이 올랐으면 {@code true} — 호출부가 "승급" 을 알릴 수 있게
     */
    public boolean addPurchase(long amount) {
        MemberGrade before = this.grade;
        this.totalPurchase += Math.max(0L, amount);
        this.grade = MemberGrade.of(this.totalPurchase);
        return this.grade != before;
    }
}
