package com.glassvue.domain.catalog.dto;

import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.entity.ProductStatus;
import java.time.Instant;
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
        Instant createdAt,
        Instant updatedAt
) {
    public static ProductResponse from(Product p) {
        return new ProductResponse(
                p.getId(), p.getName(), p.getDescription(), p.getPrice(), p.getStock(),
                p.getStatus(), p.getCategory().getId(), p.getCategory().getName(),
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
