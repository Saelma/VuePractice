package com.glassvue.domain.point.entity;

import com.glassvue.global.common.BaseTimeEntity;
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
 * 적립금 이력 한 줄 — <b>원장</b>이다.
 *
 * <p>잔액({@link PointAccount#getBalance()})은 이 이력의 합이어야 한다. 잔액만 두면
 * "왜 이 숫자지"를 나중에 따질 수 없는데, 돈에 준하는 값에서 그건 위험하다.
 *
 * <p>{@code amount} 는 <b>부호 있는 값</b>이다(적립 +, 사용 −). 부호를 {@code type} 으로 유추하지 않고
 * 값에 담으면 합계를 그냥 {@code SUM} 으로 낼 수 있고, {@code ADJUST} 처럼 양방향인 종류가 생겨도
 * 규칙이 안 바뀐다.
 *
 * <p>{@code balanceAfter} 를 함께 남기는 이유: 잔액이 어긋났을 때 <b>어느 시점부터 틀어졌는지</b>를
 * 이력만으로 짚을 수 있다.
 */
@Entity
@Getter
@Table(name = "point_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointHistory extends BaseTimeEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "member_id", columnDefinition = "RAW(16)", nullable = false, updatable = false)
    private UUID memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointType type;

    @Column(nullable = false)
    private long amount;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    /** 어느 주문 때문인지. 관리자 조정은 null. FK 아님(느슨한 참조 — order 는 다른 도메인). */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "order_id", columnDefinition = "RAW(16)")
    private UUID orderId;

    @Column(nullable = false, length = 100)
    private String reason;

    private PointHistory(UUID memberId, PointType type, long amount,
                         long balanceAfter, UUID orderId, String reason) {
        this.memberId = memberId;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.orderId = orderId;
        this.reason = reason;
    }

    public static PointHistory earned(UUID memberId, long amount, long balanceAfter,
                                      UUID orderId, String reason) {
        return new PointHistory(memberId, PointType.EARN, amount, balanceAfter, orderId, reason);
    }

    /** 사용은 <b>음수</b>로 기록한다 — 호출부가 양수를 넘겨도 여기서 부호를 붙인다(빠뜨릴 수 없게). */
    public static PointHistory used(UUID memberId, long amount, long balanceAfter,
                                    UUID orderId, String reason) {
        return new PointHistory(memberId, PointType.USE, -Math.abs(amount), balanceAfter, orderId, reason);
    }
}
