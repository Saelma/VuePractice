package com.glassvue.domain.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 부분 취소 요청 (2026-08-24, BACKLOG G-4).
 *
 * <p>⚠ <b>{@code productId} 가 아니라 {@code orderItemId} 다</b> — 같은 상품의 다른 옵션이 한 주문에
 * 둘 이상 들어올 수 있어 상품으로는 품목을 지목할 수 없다({@code OrderItemResponse.orderItemId} 주석).
 *
 * <p>⚠ <b>사유를 받지 않는다.</b> 전체 취소는 {@code orders.cancel_reason} 에 남길 자리가 있지만
 * 부분 취소는 회차마다 일어나 <b>한 칸에 담기지 않는다</b>. 관리자 조작은 원장에 회차별로 남고
 * ({@code ORDER_ITEM_CANCEL}), 고객 조작은 남길 자리가 없다 — 있는 척하지 않는다.
 * 🔴 <b>필요해지면 별도 테이블이 답이지 컬럼이 아니다</b>(V57 주석과 같은 판단).
 */
public record OrderItemCancelRequest(

        @Schema(description = "취소할 품목 — 주문 응답의 items[].orderItemId", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "취소할 품목을 지정해 주세요.")
        UUID orderItemId,

        @Schema(description = "취소할 수량. **남은 수량** 이하여야 한다(이미 일부를 취소했으면 그만큼 줄어 있다)",
                example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "취소할 수량을 입력해 주세요.")
        @Min(value = 1, message = "취소 수량은 1개 이상이어야 합니다.")
        Long quantity
) {
}
