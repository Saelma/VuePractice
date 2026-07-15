package com.glassvue.domain.order.controller;

import com.glassvue.domain.order.dto.OrderResponse;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

@Tag(name = "Order", description = "주문 API (로그인 필요)")
public interface OrderController {

    @Operation(summary = "주문 생성 (장바구니 결제)")
    ResponseEntity<ApiResponse<UUID>> checkout(@Parameter(hidden = true) AuthUser user);

    @Operation(summary = "내 주문 목록")
    ResponseEntity<ApiResponse<List<OrderResponse>>> myOrders(@Parameter(hidden = true) AuthUser user);

    @Operation(summary = "주문 상세")
    ResponseEntity<ApiResponse<OrderResponse>> get(@Parameter(hidden = true) AuthUser user, UUID id);

    @Operation(summary = "주문 취소")
    ResponseEntity<ApiResponse<Void>> cancel(@Parameter(hidden = true) AuthUser user, UUID id);
}
