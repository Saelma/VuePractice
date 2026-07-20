package com.glassvue.domain.order.controller;

import com.glassvue.domain.order.dto.AdminOrderResponse;
import com.glassvue.domain.order.dto.OrderSearchCondition;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin Order", description = "관리자 주문 API (ADMIN 전용)")
public interface AdminOrderController {

    @Operation(summary = "전체 주문 목록 (상태·구매자 필터, 페이징)",
            description = "발송 처리할 주문을 찾기 위한 관리자 목록. 사용자용 /api/orders와 달리 구매자 정보를 포함한다.")
    ResponseEntity<ApiResponse<PageResponse<AdminOrderResponse>>> list(
            @ParameterObject OrderSearchCondition condition,
            @ParameterObject Pageable pageable);
}
