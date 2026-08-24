package com.glassvue.domain.order.controller;

import com.glassvue.domain.order.dto.AdminOrderCancelRequest;
import com.glassvue.domain.order.dto.OrderCancelRequest;
import com.glassvue.domain.order.dto.OrderItemCancelRequest;
import com.glassvue.domain.order.dto.OrderResponse;
import com.glassvue.domain.order.dto.OrderSearchCondition;
import com.glassvue.global.response.PageResponse;
import com.glassvue.domain.order.dto.OrderCreateRequest;
import com.glassvue.domain.order.dto.OrderShipRequest;
import com.glassvue.domain.order.dto.ReturnRejectRequest;
import com.glassvue.domain.order.dto.ReturnRequest;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
    ResponseEntity<ApiResponse<Void>> ship(@Parameter(hidden = true) AuthUser admin, UUID id,
            OrderShipRequest request);

    @Operation(summary = "배송완료 처리 (관리자, SHIPPED→DELIVERED)",
            description = "지금은 관리자 수동 전이. 택배사 웹훅 연동은 이후 단계.")
    ResponseEntity<ApiResponse<Void>> deliver(@Parameter(hidden = true) AuthUser admin, UUID id);

    @Operation(summary = "주문 취소 (본인, ORDERED·PAID만)",
            description = """
                    **본문은 선택**이다(2026-08-04, B-17) — 사유 없이 취소할 수 있다.
                    `{"reason":"단순 변심"}` 처럼 보내면 주문에 남고, 안 보내거나 공백이면 `cancelReason` 은 **null** 이다
                    (공백을 그대로 저장하면 화면이 "사유가 있다"로 읽어 빈 칸을 그린다).

                    반품 사유(`return-request`)는 **필수**인데 이쪽만 선택인 이유: 취소는 돈이 오가기 전 단계라
                    입력을 강제하면 마찰이 값보다 크다.

                    ⚠ **V40 이전에 취소된 주문은 사유가 없다**(백필하지 않았다).
                    """)
    ResponseEntity<ApiResponse<Void>> cancel(@Parameter(hidden = true) AuthUser user, UUID id,
            @Valid OrderCancelRequest request);

    @Operation(summary = "주문 취소 — 관리자 대행 (ORDERED·PAID만)", description = """
            CS 로 "취소해 주세요" 가 들어왔을 때 관리자가 대신 취소한다 (B-25, 2026-08-10).

            **본인 취소와 같은 것**: 허용 상태(ORDERED·PAID) · 재고 복원 · 쓴 적립금 환불 · 구매자 알림.
            **다른 것**: 사유가 **필수**이고, 누가 취소했는지가 `cancelledByName` 에 남으며 감사 로그에도 남는다.

            ⚠ **발송 이후는 여기서 못 한다** — 물건이 나가 있어 회수 절차가 필요하고, 그 자리는
            반품(요청 → 관리자 승인)이 맡는다. 열어 두면 돌아오지도 않은 물건으로 재고가 복원된다.
            """)
    ResponseEntity<ApiResponse<Void>> cancelByAdmin(@Parameter(hidden = true) AuthUser admin, UUID id,
            @Valid AdminOrderCancelRequest request);

    @Operation(summary = "부분 취소 (본인, ORDERED·PAID만)", description = """
            주문의 **품목 하나에서 수량만큼** 취소한다 (G-4, 2026-08-24).

            **정산은 「금액 비례 · 내림」으로 나눈다** — 취소한 금액이 남은 상품합계에서 차지하는 비율만큼
            쿠폰 할인과 사용 적립금을 회수하고, 나머지를 돌려준다. 버려진 잔돈은 주문에 남아 있다가
            **다음 취소로 따라간다** — 품목을 하나씩 다 취소하면 환불 합계가 정확히 결제금액이 된다.

            ⚠ **쿠폰 최소주문금액은 소급하지 않는다** — 부분 취소로 기준 미달이 되어도 쿠폰은 그대로 걸려 있다.
            ⚠ **무료배송 기준도 소급하지 않는다** — 배송비(`shippingFee`)는 부분 취소로 움직이지 않는다.
            (품목을 빼면 상품합계가 낮아지므로 이 값은 «0 → 3,000» 방향으로만 움직일 텐데, 그러면
            「취소했더니 돈을 더 냈다」가 된다.)

            🔴 **마지막 품목까지 빼면 주문 자체가 `CANCELLED` 로 떨어지고** 그때 쿠폰이 복구된다.

            ⚠ **발송 이후는 못 한다**(전체 취소와 같다) — 그 자리는 반품이 맡는다.
            ⚠ 응답의 `payAmount` 는 **지금 받을 금액**이다. 원본 `totalPrice`·`couponDiscount`·`usedPoint` 는
            주문 시점 스냅샷이라 안 변하고, 빠진 몫은 `cancelledItemsTotal`·`refundedAmount`·`cancelledPoint` 로 온다.
            """)
    ResponseEntity<ApiResponse<Void>> cancelItem(@Parameter(hidden = true) AuthUser user, UUID id,
            @Valid OrderItemCancelRequest request);

    @Operation(summary = "부분 취소 — 관리자 대행 (ORDERED·PAID만)", description = """
            CS 로 "이것만 빼 주세요" 가 들어왔을 때 관리자가 대신 뺀다 (G-4, 2026-08-24).

            **본인 부분 취소와 같은 것**: 정산 배분 · 재고 복원 · 적립금 환불 · 전량이면 주문 취소.
            **다른 것**: 원장에 `ORDER_ITEM_CANCEL` 로 남는다(주문번호 · 품목명 · 수량 · 환불액).

            ⚠ **전체 취소(`admin-cancel`)와 원장 행동이 다르다** — 돈이 다르게 움직여서다.
            전체 취소는 쿠폰까지 복구되지만 부분 취소는 쿠폰이 그대로 걸려 있다.
            """)
    ResponseEntity<ApiResponse<Void>> cancelItemByAdmin(@Parameter(hidden = true) AuthUser admin, UUID id,
            @Valid OrderItemCancelRequest request);

    @Operation(summary = "반품 요청 (본인, DELIVERED만)",
            description = "관리자 승인 시 옵션 재고 복원 + 결제금액을 적립금으로 환불하고 그 주문의 적립을 회수한다.")
    ResponseEntity<ApiResponse<Void>> requestReturn(
            @Parameter(hidden = true) AuthUser user, UUID id, ReturnRequest request);

    @Operation(summary = "반품 승인 (관리자, RETURN_REQUESTED→RETURNED)")
    ResponseEntity<ApiResponse<Void>> approveReturn(@Parameter(hidden = true) AuthUser admin, UUID id);

    @Operation(summary = "반품 거절 (관리자, RETURN_REQUESTED→DELIVERED)",
            description = """
                    사유가 **필수**다(2026-08-11, V47). 거절은 상태를 남기지 않으므로
                    (`DELIVERED` 로 되돌아간다) 사유가 **「거절이 있었다」를 나타내는 유일한 표시**이고,
                    고객 화면의 반품 카드도 이 값으로 뜬다. 사유는 고객 알림 문구에도 그대로 들어간다.
                    ⚠ 승인에 본문이 없는 것과 다른 이유: 거절은 **고객의 요청을 뒤집는 결정**이라 근거가 따라와야 한다.
                    """)
    ResponseEntity<ApiResponse<Void>> rejectReturn(@Parameter(hidden = true) AuthUser admin, UUID id,
            ReturnRejectRequest request);
}
