package com.glassvue.domain.order.entity;

import com.glassvue.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
}
