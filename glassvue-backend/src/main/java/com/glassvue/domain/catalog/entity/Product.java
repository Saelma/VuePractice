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
    private long price; // 원

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

    @Builder
    private Product(String name, String description, long price, long stock,
                    ProductStatus status, UUID imageGroupId, Category category) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.status = (status != null) ? status : ProductStatus.SELLING;
        this.imageGroupId = imageGroupId;
        this.category = category;
    }

    public void update(String name, String description, long price, long stock,
                       ProductStatus status, UUID imageGroupId, Category category) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.status = status;
        this.imageGroupId = imageGroupId;
        this.category = category;
    }
}
