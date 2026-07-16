package com.glassvue.domain.review.service.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassvue.domain.review.dto.ProductReviewsResponse;
import com.glassvue.domain.review.entity.Review;
import com.glassvue.domain.review.repository.ReviewRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/**
 * ReviewQueryService 통합 — 실 DB. 요약(평균 별점·개수) + 페이지 조립, 평균 반올림 검증.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@Transactional
class ReviewQueryServiceIntegrationTest {

    @Autowired ReviewQueryService queryService;
    @Autowired ReviewRepository reviewRepository;

    private void save(UUID pid, int rating) {
        reviewRepository.save(Review.builder()
                .productId(pid).authorId(UUID.randomUUID()).author("nick").rating(rating).content("c").build());
    }

    @Test
    @DisplayName("요약: 평균 4.5(반올림) · 개수 2 · 페이지 2건")
    void summary() {
        UUID pid = UUID.randomUUID();
        save(pid, 5);
        save(pid, 4);
        ProductReviewsResponse r = queryService.getProductReviews(pid, PageRequest.of(0, 10));
        assertThat(r.averageRating()).isEqualTo(4.5);
        assertThat(r.reviewCount()).isEqualTo(2);
        assertThat(r.page().content()).hasSize(2);
    }

    @Test
    @DisplayName("리뷰 없는 상품: 평균 0.0 · 개수 0 · 빈 페이지")
    void empty() {
        ProductReviewsResponse r = queryService.getProductReviews(UUID.randomUUID(), PageRequest.of(0, 10));
        assertThat(r.averageRating()).isEqualTo(0.0);
        assertThat(r.reviewCount()).isZero();
        assertThat(r.page().content()).isEmpty();
    }
}
