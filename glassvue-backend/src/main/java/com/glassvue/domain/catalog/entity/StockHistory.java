package com.glassvue.domain.catalog.entity;

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
 * 재고 변동 한 줄 — <b>원장</b>이다 (2026-08-04, 백로그 B-19).
 *
 * <p>{@code product_variant.stock} 은 현재값 하나뿐이라 "어제 10개였는데 왜 3개지" 에 답할 수 없다.
 * 적립금 이력({@code PointHistory})이 잔액의 원장인 것과 같은 구조다.
 *
 * <p><b>원장의 성질: {@code SUM(quantity)} = 현재 재고.</b> 그래서 초기 재고
 * ({@link StockChangeReason#ADMIN_CREATE})도 첫 줄로 남긴다 — 안 남기면 합계가 늘 모자란다.
 * ⚠ 단 <b>V39 이전 상품은 백필하지 않았으므로</b> 한동안 이 성질이 성립하지 않는다(V39 주석 참조).
 *
 * <h2>🔴 왜 {@code variantId} 가 아니라 {@code productId} + {@code variantName} 으로 잇는가</h2>
 *
 * {@code ProductCommandService.update()} 는 옵션을 <b>통째로 교체</b>한다(deleteAll + saveVariants).
 * {@code ProductVariant.of()} 가 새 엔티티를 만들어 PK 를 새로 발급하므로,
 * <b>관리자가 상품을 한 번만 저장해도 모든 옵션의 id 가 바뀐다.</b> {@code variantId} 로만 조회하면
 * 편집 한 번에 이력이 통째로 끊긴다.
 *
 * <p>→ {@code order_item} 이 이미 같은 문제를 스냅샷으로 풀었다(옵션이 지워져도 과거 주문 표시가
 * 멀쩡하도록 {@code variantName} 을 실어 둔다). 같은 방식으로 <b>{@code variantName} 이 이력을 잇는
 * 실제 키</b>이고, {@code variantId} 는 느슨한 참조로만 남긴다(FK 아님, NULL 가능).
 *
 * <p>{@code actorId} 는 <b>관리자 조작에만</b> 있다 — 주문 경로는 {@code orderId} 로 누구 주문인지
 * 되짚을 수 있어 같은 정보를 두 번 적지 않는다.
 */
@Entity
@Getter
@Table(name = "stock_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockHistory extends BaseTimeEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "product_id", columnDefinition = "RAW(16)", nullable = false, updatable = false)
    private UUID productId;

    /** 옵션명 스냅샷 — 이력을 잇는 실제 키다(위 설명 참조). */
    @Column(name = "variant_name", nullable = false, length = 100)
    private String variantName;

    /** 변동 시점의 옵션 id. 편집으로 무효해질 수 있다(FK 아님). */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "variant_id", columnDefinition = "RAW(16)")
    private UUID variantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockChangeReason reason;

    /** 부호 있는 값 — 차감 −, 복원·입고 +. */
    @Column(nullable = false)
    private long quantity;

    /** 변동 <b>직후</b> 재고. 원장이 어긋났을 때 어느 줄부터 틀어졌는지 짚는 용도. */
    @Column(name = "stock_after", nullable = false)
    private long stockAfter;

    /** 어느 주문 때문인지. 관리자 조작은 null. FK 아님(느슨한 참조 — order 는 다른 도메인). */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "order_id", columnDefinition = "RAW(16)")
    private UUID orderId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "actor_id", columnDefinition = "RAW(16)")
    private UUID actorId;

    @Column(name = "actor_name", length = 50)
    private String actorName;

    private StockHistory(UUID productId, String variantName, UUID variantId, StockChangeReason reason,
                         long quantity, long stockAfter, UUID orderId, UUID actorId, String actorName) {
        this.productId = productId;
        this.variantName = variantName;
        this.variantId = variantId;
        this.reason = reason;
        this.quantity = quantity;
        this.stockAfter = stockAfter;
        this.orderId = orderId;
        this.actorId = actorId;
        this.actorName = actorName;
    }

    /**
     * 주문 차감 — <b>음수</b>로 기록한다. 호출부가 양수를 넘겨도 여기서 부호를 붙인다
     * ({@code PointHistory.used} 와 같은 이유 — 빠뜨릴 수 없게).
     */
    public static StockHistory ordered(UUID productId, String variantName, UUID variantId,
                                       long quantity, long stockAfter, UUID orderId) {
        return new StockHistory(productId, variantName, variantId, StockChangeReason.ORDER,
                -Math.abs(quantity), stockAfter, orderId, null, null);
    }

    /**
     * 주문 취소·반품 승인으로 복원 — <b>양수</b>. 취소와 반품은 재고 관점에서 같은 일이지만
     * 이유를 나눠 둔다(원장에서 "왜 돌아왔는지" 가 구분돼야 값이 있다).
     */
    public static StockHistory restored(UUID productId, String variantName, UUID variantId,
                                        StockChangeReason reason, long quantity, long stockAfter,
                                        UUID orderId) {
        return new StockHistory(productId, variantName, variantId, reason,
                Math.abs(quantity), stockAfter, orderId, null, null);
    }

    /**
     * 관리자 조작(등록 시 초기 재고 · 편집). {@code quantity} 는 <b>부호를 그대로 받는다</b> —
     * 편집은 양방향(늘리기·줄이기·옵션 삭제)이라 여기서 부호를 강제하면 틀린다.
     */
    public static StockHistory byAdmin(UUID productId, String variantName, UUID variantId,
                                       StockChangeReason reason, long quantity, long stockAfter,
                                       UUID actorId, String actorName) {
        return new StockHistory(productId, variantName, variantId, reason,
                quantity, stockAfter, null, actorId, actorName);
    }
}
