package com.glassvue.domain.order.dto;

import com.glassvue.domain.order.entity.DeliveryCarrier;
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
        // 사람이 읽는 주문번호(V15). 화면은 UUID 대신 이걸 보여준다.
        String orderNo,
        UUID memberId,
        String buyerNickname,
        OrderStatus status,
        // totalPrice는 상품 합계(배송비 제외).
        // payAmount = totalPrice − couponDiscount − usedPoint + shippingFee 가 실제 결제 금액이다.
        long totalPrice,
        long shippingFee,
        // 쿠폰 스냅샷(V17). 안 쓴 주문은 이름 null, 할인액 0.
        String couponName,
        long couponDiscount,
        // 적립금 스냅샷(V21). 쓴 적 없으면 0, 배송완료 전이면 earnedPoint 도 0이다.
        long usedPoint,
        long earnedPoint,
        long payAmount,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant paidAt,
        Instant shippedAt,
        Instant cancelledAt,
        Instant deliveredAt,
        // 반품(V24). 요청·완료 시각과 사유·환불액. 반품이 없었으면 전부 null/0.
        String returnReason,
        Instant returnRequestedAt,
        Instant returnedAt,
        long refundAmount,
        // 배송지 스냅샷(주문 시점). 배송지 도입(V11) 이전 주문은 전부 null이다.
        String shipRecipient,
        String shipPhone,
        String shipZipcode,
        String shipAddress1,
        String shipAddress2,
        // 배송 요청사항(V38, B-20). 주문 시점 스냅샷이라 주소록을 고쳐도 안 변한다.
        // null 이면 화면이 줄 자체를 안 그린다(요청 없는 주문이 대부분이라 빈 줄을 남기지 않는다).
        String shipMemo,
        // 배송 추적(V13). 운송장 도입 이전 주문은 전부 null이라 화면이 추적 영역을 감춘다.
        DeliveryCarrier shipCarrier,
        String shipCarrierName,
        String shipTrackingNo,
        String trackingUrl
) {
    /**
     * {@code trackingUrl}은 설정({@code glassvue.delivery})으로 만들어져 들어온다 —
     * 화면이 택배사별 URL 형식을 알 필요가 없게 서버가 완성해서 준다. 만들 수 없으면 null이고,
     * 그때 화면은 조회 링크를 감추고 송장번호만 보여준다.
     */
    public static OrderResponse from(Order o, String trackingUrl) {
        return new OrderResponse(
                o.getId(),
                o.getOrderNo(),
                o.getMemberId(),
                o.getBuyerNickname(),
                o.getStatus(),
                o.getTotalPrice(),
                o.getShippingFee(),
                o.getCouponName(),
                o.getCouponDiscount(),
                o.getUsedPoint(),
                o.getEarnedPoint(),
                o.getPayAmount(),
                o.getItems().stream().map(OrderItemResponse::from).toList(),
                o.getCreatedAt(),
                o.getPaidAt(),
                o.getShippedAt(),
                o.getCancelledAt(),
                o.getDeliveredAt(),
                o.getReturnReason(),
                o.getReturnRequestedAt(),
                o.getReturnedAt(),
                (o.getStatus() == com.glassvue.domain.order.entity.OrderStatus.RETURNED
                        || o.getStatus() == com.glassvue.domain.order.entity.OrderStatus.RETURN_REQUESTED)
                        ? o.refundableAmount() : 0L,
                o.getShipRecipient(),
                o.getShipPhone(),
                o.getShipZipcode(),
                o.getShipAddress1(),
                o.getShipAddress2(),
                o.getShipMemo(),
                o.getShipCarrier(),
                o.getShipCarrier() == null ? null : o.getShipCarrier().getDisplayName(),
                o.getShipTrackingNo(),
                trackingUrl);
    }
}
