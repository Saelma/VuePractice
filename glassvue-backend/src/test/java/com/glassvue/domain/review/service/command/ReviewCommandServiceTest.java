package com.glassvue.domain.review.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.domain.audit.event.AdminActionEvent;
import com.glassvue.domain.member.service.MemberService;

@ExtendWith(MockitoExtension.class)
class ReviewCommandServiceTest {

    @Mock ReviewRepository reviewRepository;
    @Mock ProductQueryService productQueryService;
    @Mock OrderService orderService;
    @Mock ImageService imageService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock MemberService memberService;
    @InjectMocks ReviewCommandService service;

    private final UUID productId = UUID.randomUUID();
    private final UUID oldGroupId = UUID.randomUUID();
    private final AuthUser user = new AuthUser(UUID.randomUUID(), Role.USER, "kim");
    private final AuthUser admin = new AuthUser(UUID.randomUUID(), Role.ADMIN, "admin");

    /** 집계 이벤트 발행은 모든 성공 경로에 있으므로 기본값을 깔아둔다(필요한 테스트만 덮어씀). */
    @BeforeEach
    void stubStats() {
        lenient().when(reviewRepository.statsByProduct(any())).thenReturn(new ReviewStats(null, 0));
    }

    private Review reviewBy(UUID authorId) {
        return Review.builder().productId(productId).authorId(authorId).author("nick").rating(5).content("c")
                .imageGroupId(oldGroupId).build(); // 교체/제거가 유의미하도록 기존 그룹을 갖고 시작
    }
    private static void assertErrorCode(Runnable r, ErrorCode expected) {
        assertThatThrownBy(r::run).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(expected);
    }

    @Test
    @DisplayName("작성: 구매하지 않은 상품 → REVIEW_NOT_PURCHASED")
    void create_notPurchased() {
        when(orderService.hasPurchased(user.id(), productId)).thenReturn(false);
        assertErrorCode(() -> service.create(productId, new ReviewCreateRequest(5, "좋아요", List.of()), user),
                ErrorCode.REVIEW_NOT_PURCHASED);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("작성: 이미 리뷰한 상품 → DUPLICATE_REVIEW")
    void create_duplicate() {
        when(orderService.hasPurchased(user.id(), productId)).thenReturn(true);
        when(reviewRepository.existsByProductIdAndAuthorId(productId, user.id())).thenReturn(true);
        assertErrorCode(() -> service.create(productId, new ReviewCreateRequest(5, "좋아요", List.of()), user),
                ErrorCode.DUPLICATE_REVIEW);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("작성: 구매자·최초 → 저장 후 id 반환")
    void create_success() {
        when(orderService.hasPurchased(user.id(), productId)).thenReturn(true);
        when(reviewRepository.existsByProductIdAndAuthorId(productId, user.id())).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
        UUID id = service.create(productId, new ReviewCreateRequest(5, "좋아요", List.of()), user);
        assertThat(id).isNotNull();
        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    @DisplayName("수정: 남의 리뷰 → REVIEW_NOT_OWNER")
    void update_notOwner() {
        Review other = reviewBy(UUID.randomUUID());
        when(reviewRepository.findById(any())).thenReturn(Optional.of(other));
        assertErrorCode(() -> service.update(UUID.randomUUID(), new ReviewUpdateRequest(3, "수정", List.of()), user),
                ErrorCode.REVIEW_NOT_OWNER);
    }

    @Test
    @DisplayName("수정: 본인 리뷰 → 반영")
    void update_owner() {
        Review mine = reviewBy(user.id());
        when(reviewRepository.findById(any())).thenReturn(Optional.of(mine));
        service.update(UUID.randomUUID(), new ReviewUpdateRequest(2, "수정됨", List.of()), user);
        assertThat(mine.getRating()).isEqualTo(2);
        assertThat(mine.getContent()).isEqualTo("수정됨");
    }

    @Test
    @DisplayName("작성: 이미지 id를 주면 그룹을 만들어 리뷰에 붙인다(포토 리뷰)")
    void create_withImages() {
        UUID groupId = UUID.randomUUID();
        List<UUID> imageIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(orderService.hasPurchased(user.id(), productId)).thenReturn(true);
        when(reviewRepository.existsByProductIdAndAuthorId(productId, user.id())).thenReturn(false);
        when(imageService.createGroup(imageIds)).thenReturn(groupId);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(productId, new ReviewCreateRequest(5, "사진 첨부", imageIds), user);

        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(captor.capture());
        assertThat(captor.getValue().getImageGroupId()).isEqualTo(groupId);
    }

    @Test
    @DisplayName("작성: 이미지가 없으면 그룹을 만들지 않는다(imageGroupId=null)")
    void create_withoutImages() {
        when(orderService.hasPurchased(user.id(), productId)).thenReturn(true);
        when(reviewRepository.existsByProductIdAndAuthorId(productId, user.id())).thenReturn(false);
        when(imageService.createGroup(List.of())).thenReturn(null); // ImageService가 빈 목록 → null
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(productId, new ReviewCreateRequest(5, "글만", List.of()), user);

        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(captor.capture());
        assertThat(captor.getValue().getImageGroupId()).isNull();
    }

    @Test
    @DisplayName("수정: 이미지 목록을 새 그룹으로 통째 교체")
    void update_replacesImageGroup() {
        UUID newGroupId = UUID.randomUUID();
        List<UUID> imageIds = List.of(UUID.randomUUID());
        Review mine = reviewBy(user.id());
        when(reviewRepository.findById(any())).thenReturn(Optional.of(mine));
        when(imageService.createGroup(imageIds)).thenReturn(newGroupId);

        service.update(UUID.randomUUID(), new ReviewUpdateRequest(4, "사진 교체", imageIds), user);

        assertThat(mine.getImageGroupId()).isEqualTo(newGroupId);
    }

    @Test
    @DisplayName("수정: 빈 이미지 목록이면 이미지 제거(imageGroupId=null)")
    void update_clearsImages() {
        Review mine = reviewBy(user.id());
        when(reviewRepository.findById(any())).thenReturn(Optional.of(mine));
        when(imageService.createGroup(List.of())).thenReturn(null);

        service.update(UUID.randomUUID(), new ReviewUpdateRequest(4, "사진 뺌", List.of()), user);

        assertThat(mine.getImageGroupId()).isNull();
    }

    @Test
    @DisplayName("삭제: 관리자는 남의 리뷰도 삭제 가능")
    void delete_admin() {
        Review other = reviewBy(UUID.randomUUID());
        when(reviewRepository.findById(any())).thenReturn(Optional.of(other));
        service.delete(UUID.randomUUID(), admin);
        verify(reviewRepository).delete(other);
    }

    // ── O-6 (2026-09-11) — 관리자는 남의 리뷰를 못 고치고, 지우면 원장에 남는다 ──────────

    @Test
    @DisplayName("🔴 수정: 관리자도 남의 리뷰는 못 고친다 → REVIEW_NOT_OWNER (O-6 · 조치는 숨김으로)")
    void update_adminOnOthers_forbidden() {
        Review other = reviewBy(UUID.randomUUID());
        when(reviewRepository.findById(any())).thenReturn(Optional.of(other));
        assertErrorCode(() -> service.update(UUID.randomUUID(), new ReviewUpdateRequest(1, "관리자가 고침", List.of()), admin),
                ErrorCode.REVIEW_NOT_OWNER);
        assertThat(other.getContent()).isEqualTo("c");   // 안 바뀌었다
    }

    @Test
    @DisplayName("🔴 삭제: 관리자가 남의 리뷰를 지우면 원장(REVIEW_DELETE)에 남는다 — 대상은 작성자, detail 에 별점·본문")
    void delete_adminOnOthers_audited() {
        UUID authorId = UUID.randomUUID();
        Review other = reviewBy(authorId);
        when(reviewRepository.findById(any())).thenReturn(Optional.of(other));
        when(memberService.loginIdOf(authorId)).thenReturn("zzauthor");

        service.delete(UUID.randomUUID(), admin);

        AdminActionEvent audit = auditEvents().getFirst();
        assertThat(audit.action()).isEqualTo(AuditAction.REVIEW_DELETE);
        assertThat(audit.actorId()).isEqualTo(admin.id());
        assertThat(audit.targetId()).isEqualTo(authorId);
        assertThat(audit.targetLogin()).isEqualTo("zzauthor");
        assertThat(audit.detail()).contains("★5").contains("nick");
    }

    @Test
    @DisplayName("대조군 — 본인이 지우면 원장에 안 남긴다(«고객 본인의 조작»)")
    void delete_owner_notAudited() {
        when(reviewRepository.findById(any())).thenReturn(Optional.of(reviewBy(user.id())));
        service.delete(UUID.randomUUID(), user);
        assertThat(auditEvents()).isEmpty();
    }

    /** 발행된 것 중 감사 이벤트만. ⚠ 집계 이벤트도 함께 나가므로 종류로 거른다. */
    private List<AdminActionEvent> auditEvents() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues().stream()
                .filter(AdminActionEvent.class::isInstance).map(AdminActionEvent.class::cast).toList();
    }

    /** 발행된 ReviewRatingChangedEvent를 잡아 반환. */
    private ReviewRatingChangedEvent capturedEvent() {
        ArgumentCaptor<ReviewRatingChangedEvent> captor =
                ArgumentCaptor.forClass(ReviewRatingChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("작성: 재계산한 집계를 이벤트에 담아 발행(구독자가 review를 되묻지 않도록)")
    void create_publishesRatingChanged() {
        when(orderService.hasPurchased(user.id(), productId)).thenReturn(true);
        when(reviewRepository.existsByProductIdAndAuthorId(productId, user.id())).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reviewRepository.statsByProduct(productId)).thenReturn(new ReviewStats(4.25, 4));

        service.create(productId, new ReviewCreateRequest(5, "좋아요", List.of()), user);

        ReviewRatingChangedEvent event = capturedEvent();
        assertThat(event.productId()).isEqualTo(productId);
        assertThat(event.averageRating()).isEqualTo(4.3); // 소수 첫째 자리 반올림
        assertThat(event.reviewCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("수정: 별점이 바뀌므로 집계 이벤트를 발행")
    void update_publishesRatingChanged() {
        Review mine = reviewBy(user.id());
        when(reviewRepository.findById(any())).thenReturn(Optional.of(mine));
        when(reviewRepository.statsByProduct(productId)).thenReturn(new ReviewStats(3.0, 2));

        service.update(UUID.randomUUID(), new ReviewUpdateRequest(2, "수정됨", List.of()), user);

        assertThat(capturedEvent().averageRating()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("삭제: 마지막 리뷰가 사라지면 평균 0.0·개수 0으로 발행")
    void delete_publishesZeroWhenLastReviewGone() {
        Review mine = reviewBy(user.id());
        when(reviewRepository.findById(any())).thenReturn(Optional.of(mine));
        when(reviewRepository.statsByProduct(productId)).thenReturn(new ReviewStats(null, 0));

        service.delete(UUID.randomUUID(), user);

        ReviewRatingChangedEvent event = capturedEvent();
        assertThat(event.averageRating()).isEqualTo(0.0);
        assertThat(event.reviewCount()).isZero();
    }

    @Test
    @DisplayName("작성 실패(구매 안 함) → 집계 이벤트 없음")
    void create_failure_noEvent() {
        when(orderService.hasPurchased(user.id(), productId)).thenReturn(false);
        assertErrorCode(() -> service.create(productId, new ReviewCreateRequest(5, "좋아요", List.of()), user),
                ErrorCode.REVIEW_NOT_PURCHASED);
        verify(eventPublisher, never()).publishEvent(any());
    }
}
