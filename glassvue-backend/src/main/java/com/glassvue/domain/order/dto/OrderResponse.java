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

        /*
         * ─────────────────────── 부분 취소가 회수해 간 몫 (2026-08-24, G-4)
         *
         * 🔴 **위 네 값은 「주문 시점 원본」이고 아래 셋은 「그 뒤 빠진 것」이다.** payAmount 만
         *    «지금» 을 말한다(뺄셈이 이미 들어 있다). 화면이 그 차이를 그릴 수 있게 둘 다 보낸다 —
         *    부분 취소가 없으면 아래는 전부 0 이라 예전 화면과 똑같이 읽힌다.
         */

        /** 부분 취소로 빠진 상품금액 누적. 남은 상품합계 = {@code totalPrice - 이 값}. */
        long cancelledItemsTotal,

        /**
         * 부분 취소로 지금까지 돌려준 <b>돈</b>의 누적.
         *
         * <p>⚠ 되돌린 적립금은 여기 안 들어간다 — 그건 계정으로 갔지 돈으로 나가지 않았다.
         * 고객이 되찾은 값어치는 «이 값 + {@code cancelledPoint}» 다.
         */
        long refundedAmount,

        /** 부분 취소로 계정에 되돌린 사용 적립금 누적. */
        long cancelledPoint,

        /*
         * ─────────────────────── 부분 반품이 회수해 간 몫 (2026-08-25, G-10)
         *
         * 🔴 **위 셋과 «짝»이다** — 취소분만 보내고 반품분을 안 보내면 화면이 «남은 값» 을 못 만든다.
         *    WA §1-2-1: 한쪽을 손댈 때 반대쪽을 열어 나란히 놓는다.
         * ⚠ **쿠폰 몫을 «직접» 보낸다** — 취소 쪽은 화면이 `cancelledItemsTotal − refundedAmount −
         *    cancelledPoint` 로 **거꾸로 풀어** 쓰고 있는데(OrderDetailView `couponTaken`),
         *    반품은 환불액에 적립금 몫이 포함돼 있어 그 역산이 성립하지 않는다. 있는 값을 보낸다.
         */

        /** 반품으로 빠진 상품금액 누적. 남은 상품합계 = {@code totalPrice - cancelledItemsTotal - 이 값}. */
        long returnedItemsTotal,

        /** 반품으로 회수된 쿠폰 할인 몫 누적(금액 비례·내림). */
        long returnedCouponDiscount,

        /** 반품으로 회수된 사용 적립금 몫 누적. ⚠ 환불액에 이미 포함돼 있다 — 남은 적립금 계산용이다. */
        long returnedPoint,

        /**
         * 🔴 반품으로 <b>회수한 배송완료 적립</b> 누적 (2026-08-25, BACKLOG §I-4).
         *
         * <p>⚠ <b>이 값이 없어서 화면이 거짓말을 했다</b> — 500P 적립된 주문에서 200P 가 회수돼도
         * 상세는 계속 «이 주문으로 <b>500원</b> 적립되었어요» 라고 말했다. {@code earnedPoint} 는
         * <b>준 것</b>이고 지금 남은 것은 «준 것 − 회수한 것» 이다.
         * <p>⚠ 부분 반품 전에는 갈릴 일이 없었다(전량 반품이면 주문이 {@code RETURNED} 라 화면이
         * 다르게 그렸다) — <b>«멀쩡한 배송완료 주문이 틀린 값을 말하는» 경우가 새로 생겼다.</b>
         */
        long reversedEarnedPoint,

        List<OrderItemResponse> items,
        Instant createdAt,
        Instant paidAt,
        Instant shippedAt,
        Instant cancelledAt,
        // 취소 사유(V40, B-17). **선택**이라 취소된 주문이어도 null 일 수 있고,
        // V40 이전에 취소된 주문은 **전부** null 이다(백필하지 않았다) — 화면은 값이 있을 때만 줄을 그린다.
        String cancelReason,
        // 🔴 취소한 관리자 닉네임 (V43, B-25). **NULL 이면 본인이 취소한 것**이다.
        // ⚠ 고객에게도 내려준다 — 남이 취소한 주문을 «내가 취소한 것» 처럼 보여주면, 고객은
        //    자기가 안 한 일을 자기가 했다고 읽는다. 그게 CS 문의로 되돌아온다.
        //    (반대로 본인 취소에 «관리자» 라고 쓸 일은 없다 — NULL 이면 화면이 줄을 안 그린다.)
        String cancelledByName,
        Instant deliveredAt,
        // 반품(V24). 요청·완료 시각과 사유·환불액. 반품이 없었으면 전부 null/0.
        String returnReason,
        Instant returnRequestedAt,
        Instant returnedAt,
        // 반품 거절(V47, 2026-08-11). ⚠ **거절은 상태를 안 남긴다**(DELIVERED 로 되돌아간다) —
        // 그래서 이 값이 «거절이 있었다» 를 화면에 알리는 유일한 근거이고, 반품 카드도 이걸로 뜬다.
        // 이 필드가 생기기 전에는 거절 후 반품 이야기가 화면에서 통째로 사라졌다(오늘 사용자 지적).
        String returnRejectedReason,
        Instant returnRejectedAt,
        /**
         * 반품 환불액 — <b>단계에 따라 뜻이 다르다</b> (2026-08-25, G-10에서 갈렸다):
         * <ul>
         *   <li>{@code RETURN_REQUESTED} — <b>승인하면 얼마인가</b>(요청된 품목의 몫). 관리자가
         *       누르기 전에 본다.</li>
         *   <li>그 밖 — <b>지금까지 실제로 돌려준 누적</b>. 부분 반품을 여러 번 하면 쌓인다.</li>
         * </ul>
         * ⚠ <b>기존 전체 반품 주문은 예전 값 그대로다</b> — {@code returned_quantity} 가 0 이라
         * 누적이 0 이고, 그때만 {@code refundableAmount()} 로 되돌아간다({@link #returnRefundOf}).
         */
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
                o.getCancelledItemsTotal(),
                o.refundedAmount(),
                o.getCancelledPoint(),
                o.getReturnedItemsTotal(),
                o.getReturnedCouponDiscount(),
                o.getReturnedPoint(),
                o.getReversedEarnedPoint(),
                o.getItems().stream().map(OrderItemResponse::from).toList(),
                o.getCreatedAt(),
                o.getPaidAt(),
                o.getShippedAt(),
                o.getCancelledAt(),
                o.getCancelReason(),
                o.getCancelledByName(),
                o.getDeliveredAt(),
                o.getReturnReason(),
                o.getReturnRequestedAt(),
                o.getReturnedAt(),
                o.getReturnRejectedReason(),
                o.getReturnRejectedAt(),
                returnRefundOf(o),
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

    /**
     * 🔴 <b>세 갈래다</b> (2026-08-25, G-10).
     *
     * <p>①요청 중이면 «승인하면 얼마» 를 미리 계산해 준다. ②실제로 돌려준 누적이 있으면 그 값이다.
     * ③둘 다 아닌데 {@code RETURNED} 이면 <b>부분 반품이 생기기 전에 반품된 옛 주문</b>이라
     * 예전과 같은 값({@code refundableAmount()})을 그대로 보여준다.
     *
     * <p>⚠ ③이 없으면 <b>기존 반품 주문의 환불액이 전부 0 으로 바뀐다</b> — 새 코드에서
     * {@code refundableAmount()} 는 «남은 것» 기준인데 옛 주문은 {@code returned_quantity} 가 0 이라
     * 남은 것이 그대로 살아 있어서다. V57 이 «기존 취소 주문의 표시는 안 바뀐다» 로 간 것과 같은 자리다.
     */
    private static long returnRefundOf(com.glassvue.domain.order.entity.Order o) {
        if (o.getStatus() == com.glassvue.domain.order.entity.OrderStatus.RETURN_REQUESTED) {
            return o.previewRequestedReturns().refundAmount();
        }
        if (o.returnRefundedAmount() > 0) {
            return o.returnRefundedAmount();
        }
        return o.getStatus() == com.glassvue.domain.order.entity.OrderStatus.RETURNED
                ? o.refundableAmount() : 0L;
    }
}
