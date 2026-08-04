package com.glassvue.domain.review.service.query;

import com.glassvue.domain.image.dto.ImageResponse;
import com.glassvue.domain.image.service.ImageService;
import java.util.stream.Collectors;
import com.glassvue.domain.catalog.dto.ProductResponse;
import com.glassvue.domain.catalog.service.query.ProductQueryService;
import com.glassvue.domain.review.dto.AdminReviewResponse;
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
    // 상품명은 catalog 의 값이다 — 엔티티를 조인하지 않고 **공개 조회 서비스**로만 가져온다
    // (도메인 간 직접 참조 금지, CLAUDE.md). cart 가 productIdsOfVariants 를 쓰는 것과 같은 방식.
    private final ProductQueryService productQueryService;

    /**
     * 상품 리뷰 목록 + 통계.
     *
     * <p>⚠ <b>{@code stats} 는 {@code photoOnly} 의 영향을 받지 않는다</b>(B-22, 2026-08-03).
     * 별점 분포·평균은 <b>그 상품 전체</b>의 것이라, 사진 필터를 걸었다고 평균이 달라지면
     * 상품 카드의 별점과 어긋난다 — 같은 상품인데 화면마다 다른 평점이 뜨는 셈이다.
     * 필터는 <b>목록에만</b> 건다.
     */
    public ProductReviewsResponse getProductReviews(UUID productId, boolean photoOnly, Pageable pageable) {
        Page<Review> page = reviewRepository.findByProduct(productId, photoOnly, pageable);
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

    /**
     * 관리자 리뷰 목록 (2026-08-04, B-18) — 상품을 가로질러 전체를 본다.
     *
     * <p>⚠ <b>숨긴 것도 함께</b> 내려간다(되돌리려면 보여야 한다). 고객 목록과 조건이 반대라
     * 리포지토리 메서드부터 나눠 두었다({@code findForAdmin}).
     *
     * <p>상품명은 <b>한 번에 모아 읽는다</b> — 리뷰마다 상품을 조회하면 N+1 이다
     * (고객 목록이 이미지 그룹을 모아 읽는 것과 같은 방식).
     * ⚠ 이름을 못 찾은 리뷰는 <b>목록에서 빼지 않고</b> 이름만 빈다 — 그 리뷰가 안 보이면
     * 관리자는 <b>고칠 수 있는 대상이 있다는 사실 자체를</b> 모르게 된다.
     */
    public PageResponse<AdminReviewResponse> findForAdmin(Boolean hidden, Pageable pageable) {
        Page<Review> page = reviewRepository.findForAdmin(hidden, pageable);

        List<UUID> productIds = page.getContent().stream().map(Review::getProductId).distinct().toList();
        Map<UUID, String> nameById = productQueryService.findByIds(productIds).stream()
                .collect(Collectors.toMap(ProductResponse::id, ProductResponse::name));

        return PageResponse.from(page.map(r ->
                AdminReviewResponse.from(r, nameById.get(r.getProductId()))));
    }
}
