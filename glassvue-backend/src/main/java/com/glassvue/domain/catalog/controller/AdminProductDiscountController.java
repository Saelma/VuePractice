package com.glassvue.domain.catalog.controller;

import com.glassvue.domain.catalog.dto.ProductDiscountRequest;
import com.glassvue.domain.catalog.dto.ProductDiscountResponse;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

/**
 * 관리자 기간 할인 API (2026-08-19, BACKLOG G-5).
 *
 * <p>🔴 <b>왜 {@code /api/products/{id}/discounts} 가 아니라 {@code /api/admin/...} 인가.</b>
 * B-25(관리자 주문 취소)는 반대로 갔다 — 관리자 주문 조작 넷이 이미 {@code /api/orders/...} 에
 * 있었기 때문이다. 여기엔 <b>그런 형제가 없다</b>(할인은 오늘 생긴 새 자원이다). 그리고 결정적인 것은
 * {@code SecurityConfig} 의 마지막 줄이 <b>{@code .anyRequest().permitAll()}</b> 이라는 사실이다 —
 * {@code /api/products/**} 아래에 두고 <b>매처를 한 줄 빠뜨리면 누구나 99% 세일을 걸 수 있다.</b>
 * {@code /api/admin/**} 아래면 이미 있는 <b>한 줄</b>이 막는다. WORKING-AGREEMENTS §2-4 가
 * 관리 API 경로를 모아 두라고 한 이유가 정확히 이것이다 — <b>잊을 수 없게 만든다.</b>
 *
 * <p>⚠ <b>고객용 조회는 여기 없다.</b> 고객이 보는 세일 정보는 상품 응답에 이미 실려 나간다
 * ({@code price}·{@code discountRate}·{@code discountEndsAt}) — 별도 엔드포인트를 두면
 * 화면이 값을 두 곳에서 받아 <b>가격과 배지가 어긋날 자리</b>가 생긴다.
 */
@Tag(name = "AdminProductDiscount", description = "관리자 기간 할인(타임세일) API")
public interface AdminProductDiscountController {

    @Operation(summary = "상품의 할인 일정 목록",
            description = """
                    지난 것·진행 중·예정을 **시간순으로 모두** 준다.
                    「지금 세일 중인가」만 주면 관리자는 **다음 세일을 언제 걸어야 겹치지 않는지**를
                    알 수 없고, 겹침 거절(400)을 만난 뒤에야 무엇과 겹쳤는지 찾게 된다.

                    `status` 는 **서버가 정한다**(`UPCOMING`·`ACTIVE`·`ENDED`) — 화면이 계산하면
                    **브라우저 시계**가 기준이 된다(B-26 에서 「오늘」을 서버가 준 것과 같은 이유).

                    날짜는 **두 벌**이다: `startDate`·`endDate` 는 폼이 그대로 다시 채울 KST 날짜(**종료일 포함**),
                    `startsAt`·`endsAt` 은 실제 경계 시각(**종료는 배타**)이다.
                    폼이 `endsAt` 을 잘라 쓰면 **종료일이 하루 뒤로 보인다.**
                    """)
    ResponseEntity<ApiResponse<List<ProductDiscountResponse>>> list(UUID productId);

    @Operation(summary = "할인 등록",
            description = """
                    **기간이 겹치면 400**(`PRODUCT-400DO`)이다. 한 상품에 같은 순간 유효한 할인이
                    둘이면 어느 것이 맞는지 답할 수 없기 때문이다 —
                    ⚠ **Oracle 유니크로는 이걸 못 막아 앱이 유일한 방어다**(G-8 과 같은 자리).

                    경계가 **맞닿는 것은 겹침이 아니다**: 종료가 배타라 「8/24 까지」와 「8/25 부터」는
                    한 순간도 함께 유효하지 않다. 연속된 세일을 이어 붙일 수 있어야 한다.

                    `endDate` 는 **포함**이다 — 관리자가 하루 빼서 적을 필요가 없다.
                    할인율은 **1~99**만 받는다(0은 할인이 아니고, 100은 결제 계산이 통째로 0원 경로가 된다).

                    ⚠ **삭제 대기 상품에는 걸 수 없다**(404) — 목록에 안 나오는 상품이라
                    걸어 봐야 관리자만 「걸었는데 아무 일도 안 난다」를 본다.
                    """)
    ResponseEntity<ApiResponse<UUID>> create(AuthUser user, UUID productId, ProductDiscountRequest request);

    @Operation(summary = "할인 수정",
            description = """
                    겹침 검사에서 **자기 자신은 뺀다** — 안 그러면 기간을 그대로 두고
                    할인율만 고치는 것이 불가능해진다.

                    ⚠ **다른 상품의 할인 id 를 넘기면 404** 다. 소속까지 확인하지 않으면
                    관리자가 **자기가 안 건드린 상품의 세일**을 바꿔 놓고 나중에야 알게 된다.
                    """)
    ResponseEntity<ApiResponse<Void>> update(AuthUser user, UUID productId, UUID discountId,
                                             ProductDiscountRequest request);

    @Operation(summary = "할인 삭제",
            description = """
                    **진행 중인 것도 지울 수 있다** — 잘못 건 세일을 되돌릴 방법이 이것뿐이다.
                    지우면 그 순간부터 원가로 돌아간다.

                    🔴 **이미 팔린 주문의 금액은 안 변한다**(주문에 정가·판매가를 복사해 두므로, B-7).
                    그 토대가 있어서 이 조작이 안전하다.
                    """)
    ResponseEntity<ApiResponse<Void>> delete(AuthUser user, UUID productId, UUID discountId);
}
