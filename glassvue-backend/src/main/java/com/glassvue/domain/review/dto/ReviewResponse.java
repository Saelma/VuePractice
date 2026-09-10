package com.glassvue.domain.review.dto;

import com.glassvue.domain.image.dto.ImageResponse;
import com.glassvue.domain.review.entity.Review;
import com.glassvue.global.security.AuthUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 리뷰 응답. {@code imageGroupId}는 노출하지 않고 이미지 목록만 내려준다(ProductResponse와 같은 규약).
 *
 * <p>🔴 <b>{@code authorId}(회원 UUID)를 싣지 않는다</b>(2026-09-10, R 축). 상품 리뷰 목록은
 * <b>비로그인도 부르는 공개 경로</b>고, 화면이 그 값으로 하는 일은 «내 글인가» 하나뿐이다.
 * PK 가 UUIDv7 이라 원시 id 는 <b>가입 시각</b>을 함께 내보낸다 — InquiryResponse 와 같은 이유다.
 */
public record ReviewResponse(
        UUID id,
        UUID productId,
        boolean mine,
        String author,
        int rating,
        String content,
        List<ImageResponse> images,
        Instant createdAt,
        Instant updatedAt
) {
    /** viewer는 비로그인 시 null. {@code mine} 은 관리자여도 남의 글이면 false 다. */
    public static ReviewResponse from(Review r, AuthUser viewer, List<ImageResponse> images) {
        return new ReviewResponse(
                r.getId(),
                r.getProductId(),
                viewer != null && r.getAuthorId() != null && r.getAuthorId().equals(viewer.id()),
                r.getAuthor(),
                r.getRating(),
                r.getContent(),
                images,
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}
