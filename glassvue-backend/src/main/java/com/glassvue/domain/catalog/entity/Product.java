package com.glassvue.domain.catalog.entity;

import com.glassvue.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Table(name = "product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Lob
    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private long price; // 원 — **실제 판매가**. 장바구니·주문·배송비 무료 기준이 전부 이 값을 쓴다.

    /**
     * 정가(할인 전 가격). null 이면 <b>할인 없음</b>이다.
     *
     * <p>{@code price} 를 정가로 바꾸지 않은 이유: 그 값은 실제로 청구되는 금액이라
     * 의미를 바꾸면 합계·무료배송 기준 계산이 전부 어긋난다. 할인율은 두 값에서 계산하고 저장하지 않는다
     * (저장하면 가격을 바꿀 때 어긋날 여지가 생긴다).
     */
    @Column(name = "list_price")
    private Long listPrice;

    @Column(nullable = false)
    private long stock; // 재고 수량

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    // 이미지 묶음 참조(느슨한 UUID). 이미지 도메인(image_group)을 가리킨다.
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "image_group_id", columnDefinition = "RAW(16)")
    private UUID imageGroupId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", columnDefinition = "RAW(16)", nullable = false)
    private Category category;

    // 리뷰 집계 비정규화 — review 도메인이 ReviewRatingChangedEvent로 밀어넣는다(catalog는 review를 모른다).
    // 목록 조회에서 조인/추가쿼리 없이 읽으려는 것. 상품 생성/수정으로는 바뀌지 않는다.
    @Column(name = "avg_rating", nullable = false)
    private double avgRating;

    @Column(name = "review_count", nullable = false)
    private long reviewCount;

    @Builder
    private Product(String name, String description, long price, Long listPrice, long stock,
                    ProductStatus status, UUID imageGroupId, Category category) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.listPrice = listPrice;
        this.stock = stock;
        this.status = (status != null) ? status : ProductStatus.SELLING;
        this.imageGroupId = imageGroupId;
        this.category = category;
    }

    public void update(String name, String description, long price, Long listPrice, long stock,
                       ProductStatus status, UUID imageGroupId, Category category) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.listPrice = listPrice;
        this.stock = stock;
        this.status = status;
        this.imageGroupId = imageGroupId;
        this.category = category;
    }
}
