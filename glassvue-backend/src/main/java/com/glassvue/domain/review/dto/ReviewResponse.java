package com.glassvue.domain.review.dto;

import com.glassvue.domain.review.entity.Review;
import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID productId,
        UUID authorId,
        String author,
        int rating,
        String content,
        Instant createdAt,
        Instant updatedAt
) {
    public static ReviewResponse from(Review r) {
        return new ReviewResponse(
                r.getId(),
                r.getProductId(),
                r.getAuthorId(),
                r.getAuthor(),
                r.getRating(),
                r.getContent(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}
