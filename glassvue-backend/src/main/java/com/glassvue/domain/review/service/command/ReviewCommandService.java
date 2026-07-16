package com.glassvue.domain.review.service.command;

import com.glassvue.domain.catalog.service.query.ProductQueryService;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.order.service.OrderService;
import com.glassvue.domain.review.dto.ReviewCreateRequest;
import com.glassvue.domain.review.dto.ReviewUpdateRequest;
import com.glassvue.domain.review.entity.Review;
import com.glassvue.domain.review.repository.ReviewRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.security.AuthUser;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리뷰 조작(command) — 작성 · 수정 · 삭제.
 * 상품 존재는 catalog 공개 서비스, 구매 인증은 order 공개 서비스로 확인한다(도메인 경계).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ReviewCommandService {

    private final ReviewRepository reviewRepository;
    private final ProductQueryService productQueryService;
    private final OrderService orderService;

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
                .build();
        Review saved = reviewRepository.save(review);
        log.info("Review created: id={} product={} by={}", saved.getId(), productId, user.id());
        return saved.getId();
    }

    public void update(UUID id, ReviewUpdateRequest req, AuthUser user) {
        Review review = findManageable(id, user);
        review.update(req.rating(), req.content());
    }

    public void delete(UUID id, AuthUser user) {
        Review review = findManageable(id, user);
        reviewRepository.delete(review);
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
