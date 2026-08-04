package com.glassvue.domain.review.dto;

import com.glassvue.domain.review.entity.Review;
import java.time.Instant;
import java.util.UUID;

/**
 * 관리자 리뷰 목록의 한 줄 (2026-08-04, 백로그 B-18).
 *
 * <p>{@link ReviewResponse} 와 <b>따로 두는 이유</b>는 답할 질문이 다르기 때문이다.
 * 고객 화면은 *"이 상품이 어떤가"* 를 묻고 상품이 이미 정해져 있지만, 관리자 목록은
 * *"지금 손봐야 할 리뷰가 있나"* 를 묻고 <b>여러 상품을 가로질러</b> 본다 — 그래서
 * {@code productName} 이 필요하고, 고객에게는 안 보이는 {@code hidden} 이 필요하다.
 *
 * <p>⚠ <b>이미지는 싣지 않는다.</b> 부적절한 리뷰를 가려내는 데 필요한 건 본문·작성자·별점이고,
 * 이미지를 실으면 목록 한 번에 그룹 조회가 따라붙는다(고객 화면이 N+1 을 피하려 한 번에 모아 읽는
 * 그 비용이다). 사진까지 봐야 하면 상품 상세에서 본다.
 *
 * @param productName 상품명 — <b>조회 시점 값</b>이다(스냅샷이 아니다). 관리자가 "무엇에 달린 리뷰인지"
 *                    알아보려는 용도라 지금 이름이 맞고, 상품이 지워졌으면 리뷰도 함께 지워진다.
 * @param hidden      숨김 여부. 관리자 목록은 <b>숨긴 것도 함께</b> 보여준다 — 되돌리려면 보여야 한다.
 */
public record AdminReviewResponse(
        UUID id,
        UUID productId,
        String productName,
        UUID authorId,
        String author,
        int rating,
        String content,
        boolean hidden,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminReviewResponse from(Review r, String productName) {
        return new AdminReviewResponse(
                r.getId(),
                r.getProductId(),
                productName,
                r.getAuthorId(),
                r.getAuthor(),
                r.getRating(),
                r.getContent(),
                r.isHidden(),
                r.getCreatedAt(),
                r.getUpdatedAt());
    }
}
