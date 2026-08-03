package com.glassvue.domain.review.controller;

import com.glassvue.domain.review.dto.ProductReviewsResponse;
import com.glassvue.domain.review.dto.ReviewCreateRequest;
import com.glassvue.domain.review.dto.ReviewUpdateRequest;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

@Tag(name = "Review", description = "상품 리뷰 API (작성은 구매자만)")
public interface ReviewController {

    @Operation(summary = "리뷰 작성 (로그인 + 해당 상품 구매자, 상품당 1회)")
    ResponseEntity<ApiResponse<UUID>> create(
            @Parameter(hidden = true) AuthUser user, UUID productId, ReviewCreateRequest request);

    @Operation(summary = "상품 리뷰 목록 + 요약(평균 별점·개수)",
            description = """
                    **정렬**은 `?sort=` 로 받는다 — 허용: `createdAt` · `updatedAt` · `rating`.
                    그 외 필드는 **400**(화이트리스트, `SortSupport`). 기본은 최신순.
                    예) 별점 높은순 `?sort=rating,desc` · 낮은순 `?sort=rating,asc`

                    **`photoOnly=true`** 면 **사진이 있는 리뷰만** 준다(B-22, 2026-08-03).

                    ⚠ **요약(평균 별점·별점 분포)은 `photoOnly` 의 영향을 받지 않는다** — 그 상품
                    **전체**의 통계다. 필터에 따라 평균이 달라지면 상품 카드의 별점과 어긋나
                    같은 상품인데 화면마다 다른 평점이 뜬다.
                    """)
    ResponseEntity<ApiResponse<ProductReviewsResponse>> list(UUID productId, boolean photoOnly, Pageable pageable);

    @Operation(summary = "리뷰 수정 (본인 또는 관리자)")
    ResponseEntity<ApiResponse<Void>> update(
            @Parameter(hidden = true) AuthUser user, UUID id, ReviewUpdateRequest request);

    @Operation(summary = "리뷰 삭제 (본인 또는 관리자)")
    ResponseEntity<ApiResponse<Void>> delete(@Parameter(hidden = true) AuthUser user, UUID id);
}
