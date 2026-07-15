package com.glassvue.domain.cart.controller;

import com.glassvue.domain.cart.dto.CartItemAddRequest;
import com.glassvue.domain.cart.dto.CartItemUpdateRequest;
import com.glassvue.domain.cart.dto.CartResponse;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

@Tag(name = "Cart", description = "장바구니 API (로그인 필요)")
public interface CartController {

    @Operation(summary = "장바구니 조회")
    ResponseEntity<ApiResponse<CartResponse>> getCart(@Parameter(hidden = true) AuthUser user);

    @Operation(summary = "장바구니 담기 (수량 증가)")
    ResponseEntity<ApiResponse<Void>> add(
            @Parameter(hidden = true) AuthUser user, @Valid CartItemAddRequest request);

    @Operation(summary = "수량 변경")
    ResponseEntity<ApiResponse<Void>> update(
            @Parameter(hidden = true) AuthUser user, UUID productId, @Valid CartItemUpdateRequest request);

    @Operation(summary = "항목 삭제")
    ResponseEntity<ApiResponse<Void>> remove(
            @Parameter(hidden = true) AuthUser user, UUID productId);

    @Operation(summary = "장바구니 비우기")
    ResponseEntity<ApiResponse<Void>> clear(@Parameter(hidden = true) AuthUser user);
}
