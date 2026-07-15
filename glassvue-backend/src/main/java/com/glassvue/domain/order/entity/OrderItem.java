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

/** 주문 시점의 상품 스냅샷(이름·가격)을 담는다 — 이후 상품이 바뀌어도 주문 내역은 그대로. */
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

    @Column(nullable = false)
    private long price;

    @Column(nullable = false)
    private long quantity;

    @Column(nullable = false)
    private long lineTotal;

    private OrderItem(UUID productId, String productName, long price, long quantity) {
        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
        this.lineTotal = price * quantity;
    }

    public static OrderItem of(UUID productId, String productName, long price, long quantity) {
        return new OrderItem(productId, productName, price, quantity);
    }

    void assignOrder(Order order) {
        this.order = order;
    }
}
