package com.glassvue.domain.catalog.controller;

import com.glassvue.domain.catalog.dto.DeletedProductResponse;
import com.glassvue.domain.catalog.dto.LowStockResponse;
import com.glassvue.domain.catalog.dto.StockHistoryResponse;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.response.PageResponse;
import com.glassvue.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
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

    @Operation(summary = "상품의 재고 변경 이력 (최신순)",
            description = """
                    재고가 왜 지금 숫자인지 되짚는 **원장**이다(2026-08-04, B-19).
                    `quantity` 는 **부호 있는 값**(주문 차감 −, 취소·반품 복원 +)이고
                    `stockAfter` 는 그 변동 **직후**의 재고다.

                    **기준은 옵션 id 가 아니라 상품 + 옵션명**이다 — 관리자가 상품을 저장하면 옵션이
                    통째로 교체돼 옵션 id 가 바뀌기 때문이다. 그래서 **지금 옵션 목록에 없는 이름**이
                    나올 수 있다(삭제된 옵션의 과거 이력).

                    `reason` 별 나머지 필드:
                    - `ORDER`·`CANCEL`·`RETURN` → `orderId` 가 있고 `actorName` 은 **null**
                      (누가 했는지는 그 주문으로 되짚는다)
                    - `ADMIN_CREATE`·`ADMIN_EDIT` → `actorName` 이 있고 `orderId` 는 **null**

                    ⚠ **V39 이전의 변동은 기록이 없다**(백필하지 않았다). 따라서 오래된 상품은
                    `quantity` 합계가 현재 재고와 다를 수 있다 — 화면이 합계로 검산하면 안 된다.

                    없는 상품 id 면 **404**다(빈 목록이 아니다).
                    """)
    ResponseEntity<ApiResponse<PageResponse<StockHistoryResponse>>> stockHistory(
            UUID id, @ParameterObject Pageable pageable);

    // ── 삭제 유예 (2026-08-12, BACKLOG F-7) ──────────────────────

    @Operation(summary = "삭제 대기 상품 목록",
            description = """
                    삭제된 상품은 **바로 사라지지 않고** 유예 기간 동안 여기 남는다(F-7).
                    각 줄은 **언제 진짜로 사라지는지**(`purgeAt`)를 함께 준다 —
                    화면이 날짜를 직접 계산하면 유예 설정을 바꿨을 때 화면만 낡는다
                    (위 `low-stock` 의 `threshold` 와 같은 규칙).

                    정렬은 **오래 기다린 것부터**다(먼저 사라질 것이 위로).
                    """)
    ResponseEntity<ApiResponse<List<DeletedProductResponse>>> deleted();

    @Operation(summary = "삭제 대기 상품 복구",
            description = """
                    상품을 되살린다. 목록·검색·상세에 다시 나오고, **장바구니에 담겨 있던 줄도
                    그대로 살아난다**(대기 중에도 줄을 지우지 않았기 때문이다).

                    ⚠ 대기 중이 아닌 상품에 불러도 **200** 이다(멱등) — 원하는 상태는 이미 이뤄져 있다.

                    🔴 **「지금 바로 지우는」 엔드포인트는 없다.** 유예를 건너뛸 수 있으면 이 기능이
                    무의미해진다 — 영구 삭제는 배치(`ProductPurgeScheduler`)만 한다.

                    ⚠ 2026-08-14 부터 **감사 원장에 남는다**(`PRODUCT_RESTORE`). 멱등 호출(대기 중이
                    아닌 상품)은 **남지 않는다** — 조작이 없었으므로 기록도 없다.
                    """)
    ResponseEntity<ApiResponse<Void>> restore(@Parameter(hidden = true) AuthUser user, UUID id);
}
