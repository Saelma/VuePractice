package com.glassvue.domain.order.controller;

import com.glassvue.domain.order.dto.OrderResponse;
import com.glassvue.domain.order.dto.OrderSearchCondition;
import com.glassvue.global.response.PageResponse;
import com.glassvue.domain.order.dto.ReturnRejectRequest;
import com.glassvue.domain.order.dto.ReturnRequest;
import com.glassvue.domain.order.service.OrderService;
import com.glassvue.domain.order.dto.AdminOrderCancelRequest;
import com.glassvue.domain.order.dto.OrderCancelRequest;
import com.glassvue.domain.order.dto.OrderItemCancelRequest;
import com.glassvue.domain.order.dto.OrderCreateRequest;
import com.glassvue.domain.order.dto.OrderShipRequest;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.security.AuthUser;
import com.glassvue.global.security.LoginUser;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderControllerImpl implements OrderController {

    private final OrderService orderService;

    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<UUID>> checkout(@LoginUser AuthUser user,
                                                      @Valid @RequestBody OrderCreateRequest request) {
        UUID id = orderService.checkout(user, request);
        return ResponseEntity.created(URI.create("/api/orders/" + id)).body(ApiResponse.ok(id));
    }

    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> myOrders(
            @LoginUser AuthUser user, OrderSearchCondition condition, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.myOrders(user.id(), condition, pageable)));
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> get(@LoginUser AuthUser user, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.get(id, user)));
    }

    @Override
    @PostMapping("/{id}/pay")
    public ResponseEntity<ApiResponse<Void>> pay(@LoginUser AuthUser user, @PathVariable UUID id) {
        orderService.pay(id, user.id());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Override
    @PostMapping("/{id}/ship")
    public ResponseEntity<ApiResponse<Void>> ship(@LoginUser AuthUser admin, @PathVariable UUID id,
                                                  @Valid @RequestBody OrderShipRequest request) {
        orderService.ship(id, admin, request.carrier(), request.trackingNo());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Override
    @PostMapping("/{id}/deliver")
    public ResponseEntity<ApiResponse<Void>> deliver(@LoginUser AuthUser admin, @PathVariable UUID id) {
        orderService.deliver(id, admin);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Override
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancel(@LoginUser AuthUser user, @PathVariable UUID id,
            // ⚠ required = false — 사유는 선택이라 **본문 없이도** 취소돼야 한다(B-17).
            //    이걸 빼면 기존 호출(본문 없는 POST)이 전부 400 이 된다.
            @Valid @RequestBody(required = false) OrderCancelRequest request) {
        orderService.cancel(id, user.id(), OrderCancelRequest.reasonOf(request));
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // 관리자 대행 취소는 관리자만 — SecurityConfig 의 /admin-cancel 매처가 막는다(ship·deliver 와 같은 방식).
    // ⚠ 조회 전용인 AdminOrderController 가 아니라 여기 둔다 — 관리자 주문 «조작» 은 발송·배송완료·
    //    반품승인/거절이 전부 /api/orders/{id}/… 에 있다(2026-08-10 실측). 새 자리를 만들면 규약이 갈린다.
    @Override
    @PostMapping("/{id}/admin-cancel")
    public ResponseEntity<ApiResponse<Void>> cancelByAdmin(@LoginUser AuthUser admin, @PathVariable UUID id,
                                                           @Valid @RequestBody AdminOrderCancelRequest request) {
        orderService.cancelByAdmin(id, admin, request.reason());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // 부분 취소(G-4). ⚠ 본인 경로는 인증만 — /api/orders/** 가 authenticated 로 덮는다.
    @Override
    @PostMapping("/{id}/cancel-item")
    public ResponseEntity<ApiResponse<Void>> cancelItem(@LoginUser AuthUser user, @PathVariable UUID id,
                                                        @Valid @RequestBody OrderItemCancelRequest request) {
        orderService.cancelItem(id, user.id(), request.orderItemId(), request.quantity());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // 관리자 대행 부분 취소는 관리자만 — SecurityConfig 의 /admin-cancel-item 매처가 막는다.
    @Override
    @PostMapping("/{id}/admin-cancel-item")
    public ResponseEntity<ApiResponse<Void>> cancelItemByAdmin(@LoginUser AuthUser admin, @PathVariable UUID id,
                                                               @Valid @RequestBody OrderItemCancelRequest request) {
        orderService.cancelItemByAdmin(id, admin, request.orderItemId(), request.quantity());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Override
    @PostMapping("/{id}/return-request")
    public ResponseEntity<ApiResponse<Void>> requestReturn(
            @LoginUser AuthUser user, @PathVariable UUID id, @Valid @RequestBody ReturnRequest request) {
        orderService.requestReturn(id, user.id(), request.reason(), request.quantitiesByItemId());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // 반품 승인·거절은 관리자만 — SecurityConfig 의 /return-approve·/return-reject 매처가 막는다(ship·deliver 와 같은 방식).
    @Override
    @PostMapping("/{id}/return-approve")
    public ResponseEntity<ApiResponse<Void>> approveReturn(@LoginUser AuthUser admin, @PathVariable UUID id) {
        orderService.approveReturn(id, admin);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Override
    @PostMapping("/{id}/return-reject")
    public ResponseEntity<ApiResponse<Void>> rejectReturn(@LoginUser AuthUser admin, @PathVariable UUID id,
                                                          @Valid @RequestBody ReturnRejectRequest request) {
        orderService.rejectReturn(id, admin, request.reason());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
