package com.glassvue.domain.review.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.glassvue.domain.review.dto.ReviewStats;
import com.glassvue.domain.review.entity.Review;
import com.glassvue.global.config.JpaAuditingConfig;
import com.glassvue.global.config.QuerydslConfig;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

/**
 * ReviewRepository 통합 — 실 Oracle. statsByProduct(avg/count JPQL) + findByProduct + 중복확인.
 * 상품은 느슨한 UUID 참조라 실제 상품 행 없이 랜덤 productId로 격리.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QuerydslConfig.class, JpaAuditingConfig.class})
class ReviewRepositoryIntegrationTest {

    @Autowired ReviewRepository reviewRepository;

    private final UUID productId = UUID.randomUUID();
    private final UUID author1 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        save(productId, author1, 5);
        save(productId, UUID.randomUUID(), 4);
        save(productId, UUID.randomUUID(), 3);
        save(UUID.randomUUID(), UUID.randomUUID(), 1); // 다른 상품
    }

    private void save(UUID pid, UUID authorId, int rating) {
        reviewRepository.save(Review.builder()
                .productId(pid).authorId(authorId).author("nick").rating(rating).content("c").build());
    }

    @Test
    @DisplayName("집계: 평균 4.0 · 개수 3")
    void stats() {
        ReviewStats s = reviewRepository.statsByProduct(productId);
        assertThat(s.count()).isEqualTo(3);
        assertThat(s.average()).isCloseTo(4.0, within(0.001));
    }

    @Test
    @DisplayName("집계: 리뷰 없는 상품 → average=null, count=0")
    void stats_empty() {
        ReviewStats s = reviewRepository.statsByProduct(UUID.randomUUID());
        assertThat(s.count()).isZero();
        assertThat(s.average()).isNull();
    }

    @Test
    @DisplayName("상품별 목록: 3건")
    void findByProduct() {
        var page = reviewRepository.findByProduct(productId, false, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(3)
                .allSatisfy(r -> assertThat(r.getProductId()).isEqualTo(productId));
    }

    @Test
    @DisplayName("중복 확인: 작성한 회원 true, 아닌 회원 false")
    void existsByProductIdAndAuthorId() {
        assertThat(reviewRepository.existsByProductIdAndAuthorId(productId, author1)).isTrue();
        assertThat(reviewRepository.existsByProductIdAndAuthorId(productId, UUID.randomUUID())).isFalse();
    }
}
