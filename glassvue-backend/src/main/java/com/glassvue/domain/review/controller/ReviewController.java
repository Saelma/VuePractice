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

    @Operation(summary = "상품 리뷰 목록 + 요약(평균 별점·개수)")
    ResponseEntity<ApiResponse<ProductReviewsResponse>> list(UUID productId, Pageable pageable);

    @Operation(summary = "리뷰 수정 (본인 또는 관리자)")
    ResponseEntity<ApiResponse<Void>> update(
            @Parameter(hidden = true) AuthUser user, UUID id, ReviewUpdateRequest request);

    @Operation(summary = "리뷰 삭제 (본인 또는 관리자)")
    ResponseEntity<ApiResponse<Void>> delete(@Parameter(hidden = true) AuthUser user, UUID id);
}
