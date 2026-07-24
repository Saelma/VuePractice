package com.glassvue.domain.catalog.entity;

import com.glassvue.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 상품 옵션(변형) — 하나의 상품에서 <b>실제로 사고 재고가 달린 단위</b> (2026-07-24, 백로그 C-8).
 *
 * <p>"검정 / M", "흰색 / L" 처럼 <b>구매 가능한 조합을 평판화</b>해 한 줄로 둔다(단일 옵션 목록 모델).
 * 옵션그룹(사이즈·색상)을 따로 모델링하지 않아 조합 폭발이 없다.
 *
 * <p>재고는 이제 {@code product} 가 아니라 <b>여기</b> 있다. "상품 1 = 재고 1" 이던 구조를
 * "상품 1 = 옵션 N, 재고는 옵션마다" 로 바꾼 것의 핵심이다.
 *
 * <p>{@code productId} 는 느슨한 UUID 지만 DB 엔 진짜 FK(CASCADE)가 걸려 있다 — catalog 도메인 <b>안</b>이라
 * MSA 로 쪼개도 product 와 함께 움직인다(member_address 가 member 에 FK 를 건 것과 같은 판단).
 */
@Entity
@Getter
@Table(name = "product_variant")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductVariant extends BaseTimeEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "product_id", columnDefinition = "RAW(16)", nullable = false, updatable = false)
    private UUID productId;

    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 기본가({@code product.price}) 대비 <b>가격차</b>. "L +2000" 이면 2000, 음수도 가능(할인 옵션).
     * 절대가가 아니라 delta 인 이유는 기본가를 바꾸면 옵션들이 함께 따라오게 하기 위해서다.
     */
    @Column(name = "price_delta", nullable = false)
    private long priceDelta;

    @Column(nullable = false)
    private long stock;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    private ProductVariant(UUID productId, String name, long priceDelta, long stock, int sortOrder) {
        this.productId = productId;
        this.name = name;
        this.priceDelta = priceDelta;
        this.stock = stock;
        this.sortOrder = sortOrder;
    }

    public static ProductVariant of(UUID productId, String name, long priceDelta, long stock, int sortOrder) {
        return new ProductVariant(productId, name, priceDelta, stock, sortOrder);
    }

    /** 이 옵션의 실제 판매가 = 기본가 + 가격차. 음수가 되면 0으로 막는다(가격은 음수일 수 없다). */
    public long effectivePrice(long basePrice) {
        return Math.max(0L, basePrice + priceDelta);
    }

    public boolean isSoldOut() {
        return stock <= 0;
    }
}
