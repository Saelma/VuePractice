package com.glassvue.domain.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 반품 요청 — 사유 + <b>반품할 품목·수량</b> (2026-08-25, BACKLOG G-10 결정 2).
 *
 * <p>🔴 <b>고객이 고른다.</b> 승인은 «고객이 요청한 대로 해 준다» 라({@code ReturnRejectRequest} 주석)
 * 관리자가 품목을 정하면 그 규약이 깨진다 — 고객이 안 말한 것을 관리자가 정하게 된다.
 *
 * <p>⚠ <b>{@code items} 는 필수다.</b> «비면 전량» 으로 하면 화면이 품목을 못 실어 보낸 버그가
 * <b>«전부 반품» 이라는 조용한 동작</b>이 된다 — 돈이 걸린 쪽에서 그런 기본값을 두지 않는다.
 * 🔴 그래서 <b>계약이 바뀌었다</b> — 프론트를 백엔드와 <b>같이</b> 배포해야 한다(WA §5).
 */
public record ReturnRequest(

        @Schema(description = "반품 사유", example = "단순 변심", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 500)
        String reason,

        @Schema(description = "반품할 품목·수량. 전량 반품이면 남은 수량을 그대로 담는다",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "반품할 품목을 하나 이상 골라 주세요.")
        @Valid
        List<Line> items
) {

    /**
     * 품목 한 줄.
     *
     * <p>⚠ <b>{@code productId} 가 아니라 {@code orderItemId} 다</b> — 같은 상품의 다른 옵션이 한
     * 주문에 둘 이상 들어올 수 있어 상품으로는 품목을 지목할 수 없다({@code OrderItemCancelRequest} 와 동일).
     */
    public record Line(

            @Schema(description = "반품할 품목 — 주문 응답의 items[].orderItemId",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "반품할 품목을 지정해 주세요.")
            UUID orderItemId,

            @Schema(description = "반품할 수량. **남은 수량** 이하여야 한다(이미 일부를 취소·반품했으면 그만큼 줄어 있다)",
                    example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "반품할 수량을 입력해 주세요.")
            @Min(value = 1, message = "반품 수량은 1개 이상이어야 합니다.")
            Long quantity
    ) {
    }

    /**
     * 서비스가 쓰는 모양으로 바꾼다.
     *
     * <p>⚠ <b>같은 품목이 두 줄로 오면 «합친다»</b> — 예외를 던지지 않는다. 그건 화면 버그지
     * 고객 잘못이 아니고, 합친 값이 남은 수량을 넘으면 <b>어차피 검증에서 400 으로 걸린다.</b>
     * {@code Collectors.toMap} 을 그냥 쓰면 여기서 {@code IllegalStateException} → <b>500</b> 이 난다.
     */
    public Map<UUID, Long> quantitiesByItemId() {
        Map<UUID, Long> merged = new LinkedHashMap<>();
        for (Line line : items) {
            merged.merge(line.orderItemId(), line.quantity(), Long::sum);
        }
        return merged;
    }
}
