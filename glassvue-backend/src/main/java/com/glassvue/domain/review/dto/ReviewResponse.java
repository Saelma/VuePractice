package com.glassvue.domain.review.dto;

import com.glassvue.domain.image.dto.ImageResponse;
import com.glassvue.domain.review.entity.Review;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 리뷰 응답. {@code imageGroupId}는 노출하지 않고 이미지 목록만 내려준다(ProductResponse와 같은 규약).
 */
public record ReviewResponse(
        UUID id,
        UUID productId,
        UUID authorId,
        String author,
        int rating,
        String content,
        List<ImageResponse> images,
        Instant createdAt,
        Instant updatedAt
) {
    public static ReviewResponse from(Review r, List<ImageResponse> images) {
        return new ReviewResponse(
                r.getId(),
                r.getProductId(),
                r.getAuthorId(),
                r.getAuthor(),
                r.getRating(),
                r.getContent(),
                images,
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}
