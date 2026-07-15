package com.glassvue.domain.catalog.dto;

import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.entity.ProductStatus;
import com.glassvue.domain.image.dto.ImageResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String description,
        long price,
        long stock,
        ProductStatus status,
        UUID categoryId,
        String categoryName,
        List<ImageResponse> images,
        Instant createdAt,
        Instant updatedAt
) {
    /** 이미지 없이 (장바구니·주문 등 이미지가 필요 없는 곳). */
    public static ProductResponse from(Product p) {
        return from(p, List.of());
    }

    public static ProductResponse from(Product p, List<ImageResponse> images) {
        return new ProductResponse(
                p.getId(), p.getName(), p.getDescription(), p.getPrice(), p.getStock(),
                p.getStatus(), p.getCategory().getId(), p.getCategory().getName(),
                images, p.getCreatedAt(), p.getUpdatedAt());
    }
}
