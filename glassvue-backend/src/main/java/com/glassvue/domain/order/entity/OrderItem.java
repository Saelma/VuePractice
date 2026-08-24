package com.glassvue.domain.order.entity;

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

/**
 * 주문 시점의 상품 스냅샷(이름·가격·이미지)을 담는다 — 이후 상품이 바뀌어도 주문 내역은 그대로.
 *
 * <p>{@code productImageUrl}은 주문 시점의 썸네일 URL이다. 상품을 참조해 조회하지 않는 이유는
 * 이름·가격과 같다 — 상품이 바뀌거나 삭제돼도 주문 이력은 그때 모습이어야 한다.
 * ⚠ 다만 상품 삭제 시 이미지 <b>파일</b>도 정리되므로 이 URL은 404가 될 수 있다.
 * 화면은 이미지 로드 실패 시 대체 표시로 넘어간다(이름·가격이라는 본질 기록은 남는다).
 */
@Entity
@Getter
@Table(name = "order_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseTimeEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", columnDefinition = "RAW(16)", nullable = false)
    private Order order;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "product_id", columnDefinition = "RAW(16)", nullable = false)
    private UUID productId;

    /**
     * 주문한 옵션(variant) — <b>취소 시 재고를 되돌릴 대상</b> (2026-07-24, C-8).
     * 느슨한 참조라 옵션이 나중에 삭제되면 dangling 이지만, 복원은 0행으로 조용히 무시된다.
     * 옵션 도입(V22) 이전 주문은 V22 가 각 상품의 기본 옵션으로 백필했다.
     */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "variant_id", columnDefinition = "RAW(16)")
    private UUID variantId;

    /** 주문 시점 옵션명 스냅샷. 단일 옵션 상품이거나 옵션 이전 주문이면 null(화면이 옵션 줄을 감춘다). */
    @Column(name = "variant_name", length = 100)
    private String variantName;

    @Column(nullable = false, length = 200)
    private String productName;

    @Column(name = "product_image_url", length = 500)
    private String productImageUrl; // 주문 시점 썸네일(없거나 삭제되면 null/404 → 화면이 대체 표시)

    @Column(nullable = false)
    private long price;

    /**
     * 주문 시점의 정가 스냅샷. null 이면 할인 없이 샀거나 정가 도입(V16) 이전 주문이다.
     *
     * <p>상품의 현재 정가를 조회해 쓰면 안 된다 — 가격은 나중에 바뀌므로 "그때 얼마에서 얼마로
     * 할인받았는지"는 주문 시점 기록이어야 한다(상품명·가격·이미지와 같은 이유).
     */
    @Column(name = "list_price")
    private Long listPrice;

    /**
     * 🔴 <b>주문 시점 «세일 전 판매가» 스냅샷</b> (2026-08-20, V55, BACKLOG G-9).
     *
     * <p>기간 할인이 없었으면 받았을 금액(기본가 + 옵션 가격차). 세일 중이 아니었으면 {@code price} 와 같다.
     *
     * <p>⚠ <b>{@link #listPrice}(정가)와 다른 값이다.</b> 정가는 관리자가 <b>손으로</b> 넣는
     * «원래 이 값어치» 라 비어 있을 수 있고, 이건 <b>서버가 계산</b>한다.
     * 🔴 실측(2026-08-20, {@code 20260820-4733}): 세일가 9,600 에 팔린 주문의 {@code list_price} 가
     * <b>NULL</b> 이라 «원래 12,000 이었다» 가 통째로 사라졌다 — 이 컬럼이 그 자리를 메운다.
     *
     * <p>⚠ <b>{@code null} 은 «이 컬럼이 생기기 전 주문» 이다</b>(백필 안 했다).
     * 세일이 없었다는 뜻이 <b>아니다</b> — 모르는 것이다(V55 주석 참조).
     */
    @Column(name = "regular_price")
    private Long regularPrice;

    @Column(nullable = false)
    private long quantity;

    @Column(nullable = false)
    private long lineTotal;

    /**
     * 🔴 <b>부분 취소된 수량</b> (2026-08-24, V57, BACKLOG G-4).
     *
     * <p>0 이면 안 취소, {@link #quantity} 와 같으면 이 품목은 전량 취소됐다. 그 사이면 일부만 빠졌다.
     *
     * <p>⚠ <b>{@code quantity} 를 깎지 않는다.</b> 주문 품목은 스냅샷이라 «몇 개를 샀나» 가 바뀌면
     * 과거 주문의 숫자가 무엇인지 알 수 없어진다({@code orders.total_price} 주석과 같은 판단).
     * 지금 살아 있는 수량은 {@link #remainingQuantity()} 로 <b>빼서</b> 얻는다.
     */
    @Column(name = "cancelled_quantity", nullable = false)
    private long cancelledQuantity;

    /**
     * 마지막 부분 취소 시각 (V57).
     *
     * <p>⚠ <b>회차별 이력이 아니다</b> — 한 품목을 수량으로 나눠 여러 번 취소하면 앞선 시각은 덮인다.
     * 이력이 필요해지면 <b>별도 테이블</b>이 답이지 컬럼을 늘리는 게 아니다({@code Order.requestReturn}
     * 주석이 같은 자리에서 정한 것). 관리자 조작은 {@code admin_audit_log} 에 회차마다 남는다.
     */
    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    private OrderItem(UUID productId, UUID variantId, String variantName,
                      String productName, String productImageUrl,
                      long price, Long regularPrice, Long listPrice, long quantity) {
        this.productId = productId;
        this.variantId = variantId;
        this.variantName = variantName;
        this.productName = productName;
        this.productImageUrl = productImageUrl;
        this.price = price;
        this.regularPrice = regularPrice;
        this.listPrice = listPrice;
        this.quantity = quantity;
        this.lineTotal = price * quantity;
    }

    public static OrderItem of(UUID productId, UUID variantId, String variantName,
                               String productName, String productImageUrl,
                               long price, Long regularPrice, Long listPrice, long quantity) {
        return new OrderItem(productId, variantId, variantName, productName,
                productImageUrl, price, regularPrice, listPrice, quantity);
    }

    void assignOrder(Order order) {
        this.order = order;
    }

    /** 아직 취소되지 않은 수량. 정산은 전부 이 값에서 나온다. */
    public long remainingQuantity() {
        return quantity - cancelledQuantity;
    }

    /** 아직 살아 있는 금액 = 단가 × 남은 수량. {@code lineTotal} 은 원본이라 안 줄어든다. */
    public long remainingAmount() {
        return price * remainingQuantity();
    }

    /** 이 품목이 통째로 빠졌나 — 주문 전체가 취소로 넘어가야 하는지 판단할 때 쓴다. */
    public boolean isFullyCancelled() {
        return cancelledQuantity >= quantity;
    }

    /**
     * 이 품목에서 {@code qty} 개를 취소한다 — <b>수량만</b> 옮기고 금액 배분은 {@link Order} 가 한다.
     *
     * <p>🔴 <b>남은 수량을 넘겨 취소할 수 없다.</b> 넘기면 환불이 결제금액보다 커진다 —
     * DB {@code ck_order_item_cancelled_qty} 가 마지막으로 잡지만 여기서 먼저 막는다.
     *
     * @return 실제로 취소된 금액 (단가 × qty)
     */
    long cancel(long qty) {
        if (qty <= 0 || qty > remainingQuantity()) {
            throw new IllegalArgumentException(
                    "취소 수량이 남은 수량을 벗어난다: qty=" + qty + ", remaining=" + remainingQuantity());
        }
        this.cancelledQuantity += qty;
        this.cancelledAt = Instant.now();
        return price * qty;
    }
}
