package com.glassvue.domain.review.dto;

import com.glassvue.global.response.PageResponse;

/**
 * 상품 리뷰 목록 응답 = 요약(평균 별점·총 개수) + 페이지.
 * 요약은 페이지가 아니라 상품 전체 리뷰 기준. 리뷰가 없으면 averageRating=0.0.
 */
public record ProductReviewsResponse(
        double averageRating,
        long reviewCount,
        PageResponse<ReviewResponse> page
) {
}
