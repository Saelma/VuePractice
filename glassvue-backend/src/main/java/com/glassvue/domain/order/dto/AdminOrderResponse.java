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
        String orderNo,
        UUID memberId,
        String buyerNickname,
        OrderStatus status,
        // 관리자 화면은 **실제로 받은 금액**(payAmount)을 보여줘야 고객이 본 숫자와 어긋나지 않는다.
        // totalPrice(상품 합계)도 함께 내려 정산 시 배송비를 갈라 볼 수 있게 한다.
        long totalPrice,
        long shippingFee,
        String couponName,
        long couponDiscount,
        long payAmount,
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
                o.getOrderNo(),
                o.getMemberId(),
                o.getBuyerNickname(),
                o.getStatus(),
                o.getTotalPrice(),
                o.getShippingFee(),
                o.getCouponName(),
                o.getCouponDiscount(),
                o.getPayAmount(),
                count,
                count <= 1 ? first : first + " 외 " + (count - 1) + "건",
                o.getCreatedAt(),
                o.getPaidAt(),
                o.getShippedAt());
    }
}
