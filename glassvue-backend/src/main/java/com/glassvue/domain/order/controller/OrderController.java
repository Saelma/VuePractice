package com.glassvue.domain.order.controller;

import com.glassvue.domain.order.dto.OrderResponse;
import com.glassvue.domain.order.dto.OrderSearchCondition;
import com.glassvue.global.response.PageResponse;
import com.glassvue.domain.order.dto.OrderCreateRequest;
import com.glassvue.domain.order.dto.OrderShipRequest;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

@Tag(name = "Order", description = "주문 API (로그인 필요)")
public interface OrderController {

    @Operation(summary = "주문 생성 (장바구니 결제)")
    ResponseEntity<ApiResponse<UUID>> checkout(@Parameter(hidden = true) AuthUser user,
            OrderCreateRequest request);

    @Operation(summary = "내 주문 목록 (상태 필터·페이징)")
    ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> myOrders(
            @Parameter(hidden = true) AuthUser user,
            @ParameterObject OrderSearchCondition condition,
            @ParameterObject Pageable pageable);

    @Operation(summary = "주문 상세 (본인, ADMIN은 전체)")
    ResponseEntity<ApiResponse<OrderResponse>> get(@Parameter(hidden = true) AuthUser user, UUID id);

    @Operation(summary = "결제 (구매자 본인, ORDERED→PAID. 실제 결제는 이후 PG 연동)")
    ResponseEntity<ApiResponse<Void>> pay(@Parameter(hidden = true) AuthUser user, UUID id);

    @Operation(summary = "발송 처리 (관리자, PAID→SHIPPED)",
            description = "택배사·송장번호를 함께 등록한다. 운송장 없이 발송 상태로 넘기면 고객이 추적할 수 없어 필수로 받는다.")
    ResponseEntity<ApiResponse<Void>> ship(UUID id, OrderShipRequest request);

    @Operation(summary = "배송완료 처리 (관리자, SHIPPED→DELIVERED)",
            description = "지금은 관리자 수동 전이. 택배사 웹훅 연동은 이후 단계.")
    ResponseEntity<ApiResponse<Void>> deliver(UUID id);

    @Operation(summary = "주문 취소 (본인, ORDERED·PAID만)")
    ResponseEntity<ApiResponse<Void>> cancel(@Parameter(hidden = true) AuthUser user, UUID id);
}
