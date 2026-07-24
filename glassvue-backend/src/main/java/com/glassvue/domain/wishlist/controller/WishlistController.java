package com.glassvue.domain.wishlist.controller;

import com.glassvue.domain.wishlist.dto.WishlistItemResponse;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

/**
 * 위시리스트(찜) API (2026-07-24, 백로그 B-6).
 *
 * <p>전부 <b>본인 것만</b> 다룬다 — 경로에 memberId 가 없고 토큰에서 읽는다.
 * 인증은 SecurityConfig 의 {@code /api/wishlist/**} 한 줄이 덮는다
 * (기본이 {@code permitAll} 이라 매처를 빠뜨리면 남의 찜이 열린다 — 2026-07-23 쿠폰에서 겪은 자리).
 */
@Tag(name = "Wishlist", description = "위시리스트(찜) API")
public interface WishlistController {

    @Operation(summary = "내 찜 목록",
            description = "최근에 찜한 것부터. 가격·재고·별점은 찜한 시점이 아니라 **지금** 값이다. "
                    + "삭제된 상품은 목록에서 빠진다.")
    ResponseEntity<ApiResponse<List<WishlistItemResponse>>> myWishlist(
            @Parameter(hidden = true) AuthUser user);

    @Operation(summary = "내가 찜한 상품 id 목록",
            description = "상품 목록·상세에서 하트를 채울지 판단하는 용도. 상품 응답에 찜 여부를 넣지 않는 이유는 "
                    + "catalog 가 wishlist 를 알게 되어 도메인 순환이 되기 때문이다.")
    ResponseEntity<ApiResponse<List<UUID>>> myProductIds(
            @Parameter(hidden = true) AuthUser user);

    @Operation(summary = "찜 추가",
            description = "이미 찜한 상품이면 조용히 성공한다(멱등). 없는 상품이면 404.")
    ResponseEntity<ApiResponse<Void>> add(
            @Parameter(hidden = true) AuthUser user,
            UUID productId);

    @Operation(summary = "찜 해제",
            description = "찜한 적 없어도 성공한다(멱등). 삭제된 상품도 해제할 수 있다.")
    ResponseEntity<ApiResponse<Void>> remove(
            @Parameter(hidden = true) AuthUser user,
            UUID productId);
}
