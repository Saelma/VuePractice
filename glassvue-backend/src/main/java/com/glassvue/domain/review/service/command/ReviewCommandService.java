package com.glassvue.domain.review.service.command;

import com.glassvue.domain.catalog.service.query.ProductQueryService;
import com.glassvue.domain.image.service.ImageService;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.order.service.OrderService;
import com.glassvue.domain.review.dto.ReviewCreateRequest;
import com.glassvue.domain.review.dto.ReviewStats;
import com.glassvue.domain.review.dto.ReviewUpdateRequest;
import com.glassvue.domain.review.entity.Review;
import com.glassvue.domain.review.event.ReviewRatingChangedEvent;
import com.glassvue.domain.review.repository.ReviewRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.security.AuthUser;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리뷰 조작(command) — 작성 · 수정 · 삭제.
 * 상품 존재는 catalog 공개 서비스, 구매 인증은 order 공개 서비스, 포토 리뷰 이미지는
 * image 공개 서비스로 다룬다(도메인 경계 — 어느 쪽 엔티티도 직접 참조하지 않는다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ReviewCommandService {

    private final ReviewRepository reviewRepository;
    private final ProductQueryService productQueryService;
    private final OrderService orderService;
    private final ImageService imageService;
    private final ApplicationEventPublisher eventPublisher;

    public UUID create(UUID productId, ReviewCreateRequest req, AuthUser user) {
        productQueryService.ensureExists(productId);
        if (!orderService.hasPurchased(user.id(), productId)) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_PURCHASED);
        }
        if (reviewRepository.existsByProductIdAndAuthorId(productId, user.id())) {
            throw new BusinessException(ErrorCode.DUPLICATE_REVIEW);
        }
        Review review = Review.builder()
                .productId(productId)
                .authorId(user.id())
                .author(user.nickname())
                .rating(req.rating())
                .content(req.content())
                .imageGroupId(imageService.createGroup(req.imageIds())) // 비면 null
                .build();
        Review saved = reviewRepository.save(review);
        publishRatingChanged(productId);
        log.info("Review created: id={} product={} by={}", saved.getId(), productId, user.id());
        return saved.getId();
    }

    public void update(UUID id, ReviewUpdateRequest req, AuthUser user) {
        Review review = findManageable(id, user);
        // 이미지는 새 그룹으로 교체(Product.update와 동일한 간단화) — 빈 목록이면 null이 되어 이미지 제거
        review.update(req.rating(), req.content(), imageService.createGroup(req.imageIds()));
        publishRatingChanged(review.getProductId());
    }

    public void delete(UUID id, AuthUser user) {
        Review review = findManageable(id, user);
        UUID productId = review.getProductId();
        reviewRepository.delete(review);
        publishRatingChanged(productId);
    }

    /**
     * 리뷰 집계를 다시 계산해 이벤트로 알린다 — 구독자(catalog의 상품 목록 비정규화)는 review가 모른다.
     *
     * <p>집계 쿼리는 JPQL이라 Hibernate가 실행 직전 **자동 플러시**하므로, 방금 save/update/delete한
     * 변경이 반영된 값이 나온다. 이벤트는 AFTER_COMMIT에 전달되니 구독자는 커밋된 값만 본다.
     */
    private void publishRatingChanged(UUID productId) {
        ReviewStats stats = reviewRepository.statsByProduct(productId);
        eventPublisher.publishEvent(
                new ReviewRatingChangedEvent(productId, stats.roundedAverage(), stats.count()));
    }

    /** 존재 확인 + (본인 리뷰이거나 ADMIN이면) 반환. 아니면 403. */
    private Review findManageable(UUID id, AuthUser user) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        boolean allowed = user.role() == Role.ADMIN || review.isOwnedBy(user.id());
        if (!allowed) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_OWNER);
        }
        return review;
    }
}
