package com.glassvue.domain.review.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewCommandServiceTest {

    @Mock ReviewRepository reviewRepository;
    @Mock ProductQueryService productQueryService;
    @Mock OrderService orderService;
    @InjectMocks ReviewCommandService service;

    private final UUID productId = UUID.randomUUID();
    private final AuthUser user = new AuthUser(UUID.randomUUID(), Role.USER, "kim");
    private final AuthUser admin = new AuthUser(UUID.randomUUID(), Role.ADMIN, "admin");

    private Review reviewBy(UUID authorId) {
        return Review.builder().productId(productId).authorId(authorId).author("nick").rating(5).content("c").build();
    }
    private static void assertErrorCode(Runnable r, ErrorCode expected) {
        assertThatThrownBy(r::run).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(expected);
    }

    @Test
    @DisplayName("작성: 구매하지 않은 상품 → REVIEW_NOT_PURCHASED")
    void create_notPurchased() {
        when(orderService.hasPurchased(user.id(), productId)).thenReturn(false);
        assertErrorCode(() -> service.create(productId, new ReviewCreateRequest(5, "좋아요"), user),
                ErrorCode.REVIEW_NOT_PURCHASED);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("작성: 이미 리뷰한 상품 → DUPLICATE_REVIEW")
    void create_duplicate() {
        when(orderService.hasPurchased(user.id(), productId)).thenReturn(true);
        when(reviewRepository.existsByProductIdAndAuthorId(productId, user.id())).thenReturn(true);
        assertErrorCode(() -> service.create(productId, new ReviewCreateRequest(5, "좋아요"), user),
                ErrorCode.DUPLICATE_REVIEW);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("작성: 구매자·최초 → 저장 후 id 반환")
    void create_success() {
        when(orderService.hasPurchased(user.id(), productId)).thenReturn(true);
        when(reviewRepository.existsByProductIdAndAuthorId(productId, user.id())).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
        UUID id = service.create(productId, new ReviewCreateRequest(5, "좋아요"), user);
        assertThat(id).isNotNull();
        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    @DisplayName("수정: 남의 리뷰 → REVIEW_NOT_OWNER")
    void update_notOwner() {
        Review other = reviewBy(UUID.randomUUID());
        when(reviewRepository.findById(any())).thenReturn(Optional.of(other));
        assertErrorCode(() -> service.update(UUID.randomUUID(), new ReviewUpdateRequest(3, "수정"), user),
                ErrorCode.REVIEW_NOT_OWNER);
    }

    @Test
    @DisplayName("수정: 본인 리뷰 → 반영")
    void update_owner() {
        Review mine = reviewBy(user.id());
        when(reviewRepository.findById(any())).thenReturn(Optional.of(mine));
        service.update(UUID.randomUUID(), new ReviewUpdateRequest(2, "수정됨"), user);
        assertThat(mine.getRating()).isEqualTo(2);
        assertThat(mine.getContent()).isEqualTo("수정됨");
    }

    @Test
    @DisplayName("삭제: 관리자는 남의 리뷰도 삭제 가능")
    void delete_admin() {
        Review other = reviewBy(UUID.randomUUID());
        when(reviewRepository.findById(any())).thenReturn(Optional.of(other));
        service.delete(UUID.randomUUID(), admin);
        verify(reviewRepository).delete(other);
    }
}
