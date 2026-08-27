package com.glassvue.domain.order.dto;

import com.glassvue.domain.order.entity.Order;
import com.glassvue.domain.order.entity.OrderItem;
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
 * <p>🔴 <b>부분 수량 다섯을 함께 내린다</b> (2026-08-27, BACKLOG §I-7). 목록만 보고는
 * <b>부분 반품 중인 {@code DELIVERED} 주문과 멀쩡한 {@code DELIVERED} 주문을 구분할 수 없었다</b> —
 * 목록에서 바로 «반품승인» 을 누르는 관리자가 <b>무엇이 몇 개 돌아오는지</b> 를 알 방법이
 * 상세로 들어가는 것밖에 없었다.
 *
 * <p>⚠ <b>수량만 내리고 금액은 안 내린다.</b> 「남은 금액」은 이미 식이 셋으로 갈려 있고
 * ({@code OrderStatsRepository.ITEM_SALES} · {@code OrderItem.remainingAmount()} · 화면),
 * 목록에 넷째를 만들 이유가 없다. 관리자가 목록에서 물어보는 것은 «몇 개가 돌아오나» 지
 * «얼마가 나가나» 가 아니다 — 금액은 상세가 답한다.
 *
 * <p>⚠ 합계는 <b>{@link OrderItem} 의 메서드로만</b> 낸다 — 여기서 {@code quantity - cancelled - returned}
 * 를 다시 쓰면 그게 <b>또 하나의 사본</b>이다(WA §1-2-1).
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
        Instant shippedAt,
        /**
         * 취소한 관리자 닉네임 스냅샷 — <b>NULL 이면 주문자 본인이 취소</b>했다는 뜻이다 (B-25, 2026-08-10).
         *
         * <p>⚠ 감사 로그에도 남지만 그건 {@code SUPER_ADMIN} 만 조회할 수 있어, <b>일반 ADMIN 이
         * «이 주문 누가 취소했지» 를 볼 유일한 경로</b>가 여기다.
         */
        String cancelledByName,
        /** 취소 사유(V40). 본인 취소는 없을 수 있고, 관리자 취소는 <b>반드시 있다</b>. */
        String cancelReason,

        /**
         * 주문 시점의 <b>원본</b> 수량 합. ⚠ {@code itemCount}(품목 «종류» 수)와 다르다 —
         * 「3종 5개」인 주문에서 {@code itemCount} 는 3, 이 값은 5다.
         *
         * <p>🔴 <b>깎지 않는다</b> — 화면이 «5개 중 2개 반품됨» 을 그리려면 원본이 필요하다
         * (상세 화면이 {@code quantity} 를 안 깎는 것과 같은 이유다).
         */
        long totalQuantity,
        /** 부분·전량 취소로 빠진 수량 합. 0 이면 취소가 없다. */
        long cancelledQuantity,
        /** 부분·전량 반품으로 <b>이미 빠진</b> 수량 합. 0 이면 반품이 없다. */
        long returnedQuantity,
        /**
         * 🔴 <b>승인 대기 중인 반품 요청 수량 합</b> — 이 항목의 핵심이다.
         *
         * <p>목록의 «반품승인» 버튼이 <b>무엇을 승인하는지</b> 를 말해 주는 유일한 값이다.
         * ⚠ <b>아직 안 빠진 것</b>이라 {@code returnedQuantity} 와 자리를 나눈다 — 합치면
         * «돌아온 것» 과 «돌아올 것» 이 섞인다.
         */
        long returnRequestedQuantity,
        /** 아직 살아 있는 수량 합. 0 이면 주문이 통째로 비었다. */
        long remainingQuantity
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
                o.getShippedAt(),
                o.getCancelledByName(),
                o.getCancelReason(),
                sum(o, OrderItem::getQuantity),
                sum(o, OrderItem::getCancelledQuantity),
                sum(o, OrderItem::getReturnedQuantity),
                sum(o, OrderItem::getReturnRequestedQuantity),
                sum(o, OrderItem::remainingQuantity));
    }

    /**
     * 품목 수량 합. ⚠ <b>식을 여기 옮겨 적지 않으려고</b> 메서드 참조만 받는다 —
     * {@code remainingQuantity} 도 {@link OrderItem#remainingQuantity()} 를 <b>불러서</b> 더한다.
     */
    private static long sum(Order o, java.util.function.ToLongFunction<OrderItem> of) {
        return o.getItems().stream().mapToLong(of).sum();
    }
}
