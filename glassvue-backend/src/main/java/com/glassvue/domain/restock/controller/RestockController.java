package com.glassvue.domain.restock.controller;

import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

/**
 * 재입고 알림 신청 API (B-9).
 *
 * <p>전부 <b>본인 것만</b> 다룬다 — 경로에 memberId 가 없고 토큰에서 읽는다. 인증은 SecurityConfig 의
 * {@code /api/restock/**} 한 줄이 덮는다(기본이 permitAll 이라 매처를 빠뜨리면 남의 신청이 열린다).
 * 화면은 품절 상품에서만 신청 버튼을 보여주지만, 서버는 상태와 무관하게 멱등 신청을 받는다
 * (재입고 이벤트는 상품 총재고 0→양수에서만 나므로, 재고 있는 상품에 신청해도 무해하게 대기할 뿐이다).
 */
@Tag(name = "Restock", description = "재입고 알림 신청 API")
public interface RestockController {

    @Operation(summary = "내가 재입고 신청한 상품 id 목록",
            description = "상품 상세에서 버튼 상태(신청함/안함)를 채우는 용도. 상품 응답에 신청 여부를 넣지 않는 이유는 "
                    + "catalog 가 restock 을 알게 되어 도메인 순환이 되기 때문이다(위시리스트와 같다).")
    ResponseEntity<ApiResponse<List<UUID>>> myProductIds(
            @Parameter(hidden = true) AuthUser user);

    @Operation(summary = "재입고 알림 신청",
            description = "이미 신청한 상품이면 조용히 성공한다(멱등). 없는 상품이면 404.")
    ResponseEntity<ApiResponse<Void>> subscribe(
            @Parameter(hidden = true) AuthUser user,
            UUID productId);

    @Operation(summary = "재입고 알림 신청 취소",
            description = "신청한 적 없어도 성공한다(멱등). 삭제된 상품도 취소할 수 있다.")
    ResponseEntity<ApiResponse<Void>> unsubscribe(
            @Parameter(hidden = true) AuthUser user,
            UUID productId);
}
