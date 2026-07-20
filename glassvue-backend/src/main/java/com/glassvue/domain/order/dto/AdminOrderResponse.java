package com.glassvue.domain.order.dto;

import com.glassvue.domain.order.entity.Order;
import com.glassvue.domain.order.entity.OrderStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * 관리자 주문 목록 항목.
 *
 * <p>사용자용 {@link OrderResponse}와 분리한 이유는 두 가지다.
 * ①관리자만 봐야 하는 구매자 정보가 들어간다. ②목록이라 품목 전체가 아니라 **요약**만 필요하다
 * (품목 전체는 상세 조회에서 본다).
 *
 * @param buyerNickname 주문 시점 스냅샷 — 탈퇴한 회원의 주문도 구매자를 알 수 있다
 * @param summary       "지바 외 2건" 형태의 품목 요약
 */
public record AdminOrderResponse(
        UUID id,
        UUID memberId,
        String buyerNickname,
        OrderStatus status,
        long totalPrice,
        int itemCount,
        String summary,
        Instant createdAt,
        Instant paidAt,
        Instant shippedAt
) {
    public static AdminOrderResponse from(Order o) {
        int count = o.getItems().size();
        String first = count == 0 ? "" : o.getItems().get(0).getProductName();
        return new AdminOrderResponse(
                o.getId(),
                o.getMemberId(),
                o.getBuyerNickname(),
                o.getStatus(),
                o.getTotalPrice(),
                count,
                count <= 1 ? first : first + " 외 " + (count - 1) + "건",
                o.getCreatedAt(),
                o.getPaidAt(),
                o.getShippedAt());
    }
}
