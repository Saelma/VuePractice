package com.glassvue.domain.order.dto;

import com.glassvue.domain.order.entity.Order;
import com.glassvue.domain.order.entity.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 주문 응답.
 *
 * <p>{@code memberId}는 화면이 **"내 주문인가"** 를 판단하는 데 쓴다 — 결제·취소 버튼은
 * 역할(ADMIN/USER)이 아니라 소유 여부로 갈려야 한다(백엔드 pay/cancel이 findByIdAndMemberId로
 * 본인만 허용하는 것과 같은 규칙). 관리자도 직접 구매할 수 있으므로 role로 가르면 어긋난다.
 *
 * <p>{@code buyerNickname}은 주문 시점 스냅샷(V5). 관리자가 목록({@code AdminOrderResponse})에서
 * 상세로 들어와도 "누구 주문인지"를 잃지 않게 상세 응답에도 싣는다. 본인 주문이면 자기 닉네임이라
 * 노출 문제는 없다.
 */
public record OrderResponse(
        UUID id,
        UUID memberId,
        String buyerNickname,
        OrderStatus status,
        long totalPrice,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant paidAt,
        Instant shippedAt
) {
    public static OrderResponse from(Order o) {
        return new OrderResponse(
                o.getId(),
                o.getMemberId(),
                o.getBuyerNickname(),
                o.getStatus(),
                o.getTotalPrice(),
                o.getItems().stream().map(OrderItemResponse::from).toList(),
                o.getCreatedAt(),
                o.getPaidAt(),
                o.getShippedAt());
    }
}
