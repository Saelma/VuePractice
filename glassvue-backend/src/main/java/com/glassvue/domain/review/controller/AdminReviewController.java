package com.glassvue.domain.review.controller;

import com.glassvue.domain.review.dto.AdminReviewResponse;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

/**
 * 리뷰 관리 (관리자) — 2026-08-04, 백로그 B-18.
 *
 * <p>이 태그가 생기기 전까지 <b>관리자 리뷰 API 는 0개</b>였다(실측). 작성자 본인만 지울 수 있어서
 * 욕설·광고 리뷰가 올라오면 아무도 손댈 수 없었다.
 */
@Tag(name = "AdminReview", description = "리뷰 관리 (관리자)")
public interface AdminReviewController {

    @Operation(summary = "리뷰 목록 (관리자, 숨김 포함)",
            description = """
                    상품을 **가로질러** 전체 리뷰를 본다(고객 목록은 상품별이다).

                    `hidden` 을 안 보내면 **전체**, `true` 면 숨긴 것만, `false` 면 보이는 것만이다.
                    관리자 목록은 **숨긴 것도 함께** 보여준다 — 되돌리려면 보여야 하기 때문이다.

                    정렬은 `createdAt`·`updatedAt`·`rating` 만 받는다(그 밖은 400).
                    기본은 최신순.

                    ⚠ `productName` 은 **조회 시점 값**이다(스냅샷이 아니다). 이미지는 싣지 않는다 —
                    가려내는 데 필요한 건 본문·작성자·별점이고, 사진까지 봐야 하면 상품 상세에서 본다.
                    """)
    ResponseEntity<ApiResponse<PageResponse<AdminReviewResponse>>> list(
            Boolean hidden, @ParameterObject Pageable pageable);

    @Operation(summary = "리뷰 숨김 (관리자)",
            description = """
                    **삭제가 아니다** — 되돌릴 수 있게 원문을 남긴다.

                    숨기면 세 가지가 함께 움직인다:
                    - 상품 리뷰 목록에서 **빠진다**(⚠ **작성자 본인에게도 안 보인다**)
                    - 상품의 **평균 별점·리뷰 수에서 빠진다**(보이지도 않는 리뷰가 별점을 끌어내리면 안 된다)
                    - 🔴 다만 **상품당 1회 제한에는 그대로 센다** — 안 그러면 숨기자마자
                      작성자가 새 리뷰를 써서 숨김이 무의미해진다

                    이미 숨겨진 리뷰에 다시 보내면 **아무 일도 하지 않는다**(집계도 다시 내지 않는다).
                    """)
    ResponseEntity<ApiResponse<Void>> hide(UUID id);

    @Operation(summary = "리뷰 숨김 해제 (관리자)",
            description = "숨김을 되돌린다. 목록·별점 집계에 다시 들어간다(관리자가 잘못 판단했을 때의 경로다).")
    ResponseEntity<ApiResponse<Void>> unhide(UUID id);
}
