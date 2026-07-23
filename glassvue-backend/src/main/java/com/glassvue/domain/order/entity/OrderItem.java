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

    @Column(nullable = false)
    private long quantity;

    @Column(nullable = false)
    private long lineTotal;

    private OrderItem(UUID productId, String productName, String productImageUrl,
                      long price, Long listPrice, long quantity) {
        this.productId = productId;
        this.productName = productName;
        this.productImageUrl = productImageUrl;
        this.price = price;
        this.listPrice = listPrice;
        this.quantity = quantity;
        this.lineTotal = price * quantity;
    }

    public static OrderItem of(UUID productId, String productName, String productImageUrl,
                               long price, Long listPrice, long quantity) {
        return new OrderItem(productId, productName, productImageUrl, price, listPrice, quantity);
    }

    void assignOrder(Order order) {
        this.order = order;
    }
}
