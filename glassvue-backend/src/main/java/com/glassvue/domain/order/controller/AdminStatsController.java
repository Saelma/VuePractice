package com.glassvue.domain.order.controller;

import com.glassvue.domain.order.dto.SalesOverviewResponse;
import com.glassvue.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

/**
 * 관리자 매출 통계 API (2026-07-24, 백로그 C-11).
 *
 * <p>경로가 {@code /api/admin/**} 이라 SecurityConfig 의 <b>한 줄</b>이 이미 ADMIN 으로 막는다 —
 * 개별 매처를 잊을 수 없게 관리 API 경로를 모아 둔 §2-4 규약이 여기서도 값을 했다.
 * 매출은 새 나가면 안 되는 정보라 이 자리가 특히 중요하다.
 */
@Tag(name = "AdminStats", description = "관리자 매출 통계 API")
public interface AdminStatsController {

    @Operation(summary = "매출 대시보드 (요약 · 일별 추이 · 상품별 TOP)",
            description = """
                    매출 대상은 **PAID · SHIPPED · DELIVERED** 주문이고 시각 기준은 **paid_at**이다.
                    결제 후 취소된 주문은 환불이므로 제외된다.
                    일자는 **KST 기준**이며, 상품매출(상품합계 − 쿠폰할인)과 배송비를 나눠서 준다.
                    상품별 판매액은 쿠폰이 주문 단위라 **할인 전** 기준이다.
                    """)
    ResponseEntity<ApiResponse<SalesOverviewResponse>> overview();
}
