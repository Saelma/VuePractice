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
 * <p>🔴 <b>상품이 지워져도 리뷰는 남는다</b>(2026-08-12 실측). {@code review} 에는 상품 FK 가 없고
 * ({@code user_constraints} 0건) {@code ProductCommandService.delete} 도 리뷰를 안 건드린다 —
 * ARCHITECTURE 의 「느슨한 UUID 참조」 설계 그대로다. 그래서 <b>이름을 못 찾는 줄이 생긴다.</b>
 * ⚠ 그 줄을 <b>목록에서 빼지 않는다</b>({@code ReviewQueryService.findForAdmin} 의 판단) — 안 보이면
 * 관리자는 고칠 대상이 있다는 사실 자체를 모른다.
 *
 * @param productName    상품명 — <b>조회 시점 값</b>이다(스냅샷이 아니다). 관리자가 "무엇에 달린 리뷰인지"
 *                       알아보려는 용도라 지금 이름이 맞다. <b>상품이 지워졌으면 {@code null}</b> 이다.
 * @param productDeleted 상품이 사라졌는지. 🔴 <b>판정은 여기 한 곳</b>이고 화면은 문구만 고른다 —
 *                       "이름이 비었다 = 삭제됐다" 를 화면이 다시 판정하면 두 곳이 갈린다.
 *                       ⚠ 빈칸으로 두면 <b>「데이터가 잘못됐다」로 읽힌다</b>(DESIGN §7, 재고 이력에서
 *                       배운 것과 같다: 빈칸은 "사유가 없다" 로 읽힌다).
 *                       ⚠ 이름을 못 찾는 이유는 <b>삭제 하나뿐</b>이다 — 조회({@code findByIds})가
 *                       {@code findAllById} 라 판매중지·숨김 상품도 이름은 그대로 실린다(2026-08-12 확인).
 * @param hidden         숨김 여부. 관리자 목록은 <b>숨긴 것도 함께</b> 보여준다 — 되돌리려면 보여야 한다.
 */
public record AdminReviewResponse(
        UUID id,
        UUID productId,
        String productName,
        boolean productDeleted,
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
                productName == null,
                r.getAuthorId(),
                r.getAuthor(),
                r.getRating(),
                r.getContent(),
                r.isHidden(),
                r.getCreatedAt(),
                r.getUpdatedAt());
    }
}
