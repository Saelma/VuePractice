package com.glassvue.domain.review.service.query;

import com.glassvue.domain.image.dto.ImageResponse;
import com.glassvue.domain.image.service.ImageService;
import com.glassvue.domain.review.dto.ProductReviewsResponse;
import com.glassvue.domain.review.dto.ReviewResponse;
import com.glassvue.domain.review.dto.ReviewStats;
import com.glassvue.domain.review.entity.Review;
import com.glassvue.domain.review.repository.ReviewRepository;
import com.glassvue.global.response.PageResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final ImageService imageService;

    public ProductReviewsResponse getProductReviews(UUID productId, Pageable pageable) {
        Page<Review> page = reviewRepository.findByProduct(productId, pageable);
        ReviewStats stats = reviewRepository.statsByProduct(productId);

        // 페이지 리뷰들의 이미지 그룹을 한 번에 조회 (N+1 회피 — ProductQueryService.search와 같은 방식)
        List<UUID> groupIds = page.getContent().stream()
                .map(Review::getImageGroupId).filter(Objects::nonNull).toList();
        Map<UUID, List<ImageResponse>> imagesByGroup = imageService.findByGroups(groupIds);

        PageResponse<ReviewResponse> mapped = PageResponse.from(page.map(r -> ReviewResponse.from(
                r,
                r.getImageGroupId() == null
                        ? List.of()
                        : imagesByGroup.getOrDefault(r.getImageGroupId(), List.of()))));
        return new ProductReviewsResponse(stats.roundedAverage(), stats.count(), mapped);
    }
}
