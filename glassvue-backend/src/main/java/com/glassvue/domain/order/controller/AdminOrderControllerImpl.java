package com.glassvue.domain.order.controller;

import com.glassvue.domain.order.dto.AdminOrderResponse;
import com.glassvue.domain.order.dto.OrderSearchCondition;
import com.glassvue.domain.order.service.OrderService;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.domain.order.entity.OrderStatus;
import com.glassvue.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 주문 API. 사용자용 {@link OrderControllerImpl}과 경로를 분리한 이유:
 * SecurityConfig에서 {@code /api/admin/**} 한 줄로 보호할 수 있어 권한 설정 사고가 잘 안 나고,
 * 응답도 관리자용(구매자 정보 포함)으로 따로 설계할 수 있다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/orders")
public class AdminOrderControllerImpl implements AdminOrderController {

    private final OrderService orderService;

    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminOrderResponse>>> list(
            OrderSearchCondition condition, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.adminOrders(condition, pageable)));
    }

    @Override
    @GetMapping("/counts")
    public ResponseEntity<ApiResponse<Map<OrderStatus, Long>>> counts() {
        return ResponseEntity.ok(ApiResponse.ok(orderService.adminOrderCounts()));
    }

    @Override
    @GetMapping("/by-member/{memberId}")
    public ResponseEntity<ApiResponse<PageResponse<AdminOrderResponse>>> byMember(
            @PathVariable UUID memberId, OrderSearchCondition condition, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.adminOrdersOf(memberId, condition, pageable)));
    }
}
