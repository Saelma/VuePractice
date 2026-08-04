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
        UUID oldGroupId = review.getImageGroupId();
        // 이미지는 새 그룹으로 교체(Product.update와 동일한 간단화) — 빈 목록이면 null이 되어 이미지 제거
        review.update(req.rating(), req.content(), imageService.createGroup(req.imageIds()));
        // createGroup 뒤에 호출해야 한다 — 그래야 옛 그룹엔 사용자가 뺀 이미지만 남는다.
        imageService.deleteGroup(oldGroupId);
        publishRatingChanged(review.getProductId());
    }

    public void delete(UUID id, AuthUser user) {
        Review review = findManageable(id, user);
        UUID productId = review.getProductId();
        UUID imageGroupId = review.getImageGroupId();
        reviewRepository.delete(review);
        imageService.deleteGroup(imageGroupId); // 리뷰가 사라지면 첨부 사진도 주인이 없다
        publishRatingChanged(productId);
    }

    /**
     * 관리자 숨김·해제 (2026-08-04, B-18) — <b>삭제가 아니다</b>(되돌릴 수 있어야 한다).
     *
     * <p>권한은 경로(<code>/api/admin/**</code>)가 건다(WA §2-4). 여기서 역할을 다시 보지 않는 이유는
     * {@code findManageable} 과 다르다 — 저건 <b>본인이거나 관리자</b>를 가리는 자리고, 이건
     * <b>관리자만</b> 닿는 경로라 매처가 이미 답을 냈다.
     *
     * <p>⚠ <b>상태가 실제로 바뀔 때만 집계를 다시 낸다.</b> 이미 숨겨진 것을 또 숨기는 요청에
     * 이벤트를 발행하면 상품 목록 캐시가 헛되이 비워진다(같은 값을 다시 써 넣으면서).
     */
    @Transactional
    public void setHidden(UUID id, boolean hidden) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        if (review.setHidden(hidden)) {
            // 숨긴 리뷰는 별점 집계에서 빠지므로(statsByProduct) 상품의 평균·개수가 달라진다.
            publishRatingChanged(review.getProductId());
        }
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
