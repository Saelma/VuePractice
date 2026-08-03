package com.glassvue.domain.catalog.controller;

import com.glassvue.domain.catalog.dto.LowStockResponse;
import com.glassvue.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

/**
 * 관리자 상품 API (2026-08-03, 백로그 B-16).
 *
 * <p>⚠ <b>상품 등록·수정·삭제는 여기가 아니라 {@code /api/products} 에 있다</b>(POST/PUT/DELETE 에
 * 개별 매처로 ADMIN 이 걸려 있다). 이 컨트롤러는 <b>관리자만 볼 수 있는 조회</b>를 위한 자리다 —
 * 재고 부족 같은 운영 정보는 고객에게 보일 이유가 없다.
 *
 * <p>경로가 {@code /api/admin/**} 이라 SecurityConfig 의 <b>한 줄</b>이 이미 ADMIN 으로 막는다
 * (WORKING-AGREEMENTS §2-4 — 개별 매처를 잊을 수 없게 관리 API 경로를 모아 두는 규약).
 */
@Tag(name = "AdminProduct", description = "관리자 상품 조회 API")
public interface AdminProductController {

    @Operation(summary = "재고 부족 옵션 (대시보드용)",
            description = """
                    재고가 **`catalog.low-stock-threshold` 이하**인 옵션을 재고 적은 순으로 준다.
                    기준값은 **재고 부족 알림(`StockRunningLowEvent`)과 같은 값**이며 응답의 `threshold` 로 함께 내려간다
                    — 화면이 기준을 따로 적으면 설정을 바꿨을 때 문구가 거짓말을 하기 때문이다.

                    **숨김(HIDDEN) 상품은 제외**된다(팔지 않는 상품은 채울 이유가 없다).
                    품절(SOLD_OUT) 표시가 붙은 상품은 **포함**된다 — 재입고가 필요한 건 그대로다.

                    `count` 는 전체 건수, `items` 는 상위 몇 줄이라 **`items.size()` 가 `count` 보다 작을 수 있다.**
                    """)
    ResponseEntity<ApiResponse<LowStockResponse>> lowStock();
}
