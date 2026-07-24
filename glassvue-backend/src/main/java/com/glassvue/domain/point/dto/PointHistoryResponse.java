package com.glassvue.domain.point.dto;

import com.glassvue.domain.point.entity.PointHistory;
import com.glassvue.domain.point.entity.PointType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** 적립금 이력 한 줄. {@code amount} 는 부호 있는 값이다(적립 +, 사용 −). */
public record PointHistoryResponse(
        UUID id,
        PointType type,
        @Schema(description = "부호 있는 증감액 (적립 +, 사용 −)", example = "-500") long amount,
        @Schema(description = "이 거래 직후 잔액", example = "700") long balanceAfter,
        @Schema(description = "관련 주문 id. 관리자 조정이면 null") UUID orderId,
        String reason,
        Instant createdAt
) {

    public static PointHistoryResponse from(PointHistory h) {
        return new PointHistoryResponse(h.getId(), h.getType(), h.getAmount(),
                h.getBalanceAfter(), h.getOrderId(), h.getReason(), h.getCreatedAt());
    }
}
