package com.glassvue.domain.review.service.query;

import com.glassvue.domain.review.dto.ProductReviewsResponse;
import com.glassvue.domain.review.dto.ReviewResponse;
import com.glassvue.domain.review.dto.ReviewStats;
import com.glassvue.domain.review.entity.Review;
import com.glassvue.domain.review.repository.ReviewRepository;
import com.glassvue.global.response.PageResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리뷰 조회(query) — 상품별 목록 + 요약(평균 별점·개수). 읽기 전용.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewQueryService {

    private final ReviewRepository reviewRepository;

    public ProductReviewsResponse getProductReviews(UUID productId, Pageable pageable) {
        Page<Review> page = reviewRepository.findByProduct(productId, pageable);
        ReviewStats stats = reviewRepository.statsByProduct(productId);

        double average = stats.average() == null ? 0.0 : Math.round(stats.average() * 10) / 10.0;
        PageResponse<ReviewResponse> mapped = PageResponse.from(page.map(ReviewResponse::from));
        return new ProductReviewsResponse(average, stats.count(), mapped);
    }
}
