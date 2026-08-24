package com.glassvue.domain.order.dto;

import com.glassvue.domain.order.entity.OrderItem;
import java.util.UUID;

/** {@code productImageUrl}은 주문 시점 썸네일 스냅샷 — 상품이 바뀌거나 삭제돼도 그때 모습을 보여준다. */
public record OrderItemResponse(
        UUID productId,
        // 옵션(variant) id — "다시 담기"(재구매)가 장바구니에 담을 때 쓴다(장바구니는 옵션 단위, B-8).
        // 스냅샷이라 그 사이 옵션이 삭제됐을 수 있다 → 담기 실패는 화면이 안내한다.
        UUID variantId,
        String productName,
        // 옵션명 스냅샷. 단일 옵션/옵션 이전 주문이면 null(화면이 옵션 줄을 감춘다).
        String optionName,
        String productImageUrl,
        long price,
        /**
         * 🔴 주문 시점 <b>세일 전 판매가</b> 스냅샷 (2026-08-20, V55, G-9).
         *
         * <p>화면은 {@code regularPrice > price} 일 때 «12,000 → 9,600» 처럼 그린다.
         * ⚠ <b>{@code null} 이면 이 컬럼이 생기기 전 주문</b>이다 — 세일이 없었다는 뜻이 아니라
         * <b>모르는 것</b>이라, 화면은 그때 세일 표시를 <b>안 한다</b>(추측해서 그리지 않는다).
         */
        Long regularPrice,
        // 주문 시점 정가 스냅샷(V16). null이면 할인 없이 샀거나 정가 도입 이전 주문이다.
        Long listPrice,
        long quantity,
        long lineTotal,

        /**
         * 🔴 <b>이 품목을 가리키는 id</b> (2026-08-24, G-4). 부분 취소가 「어느 품목」을 지목해야 해서
         * 처음으로 필요해졌다.
         *
         * <p>⚠ <b>{@code productId} 로는 지목할 수 없다</b> — 같은 상품의 다른 옵션이 한 주문에 둘 이상
         * 들어올 수 있어서다({@code ZZ-세일검증} 의 「기본」·「L」이 그 모양이다). {@code variantId} 도
         * 안전하지 않다: 스냅샷이라 옵션이 지워지면 {@code null} 이 될 수 있는 느슨한 참조다.
         */
        UUID orderItemId,

        /** 부분 취소된 수량(G-4). 0 이면 안 빠졌고, {@code quantity} 와 같으면 통째로 빠졌다. */
        long cancelledQuantity,

        /**
         * 아직 살아 있는 수량 = {@code quantity - cancelledQuantity}.
         *
         * <p>⚠ <b>{@code quantity} 를 깎아서 주지 않는다</b> — 화면이 «3개 중 1개 취소됨» 을 그리려면
         * 둘 다 필요하다. 깎아서 주면 «원래 몇 개를 샀나» 가 응답에서 사라진다.
         */
        long remainingQuantity
) {
    public static OrderItemResponse from(OrderItem i) {
        return new OrderItemResponse(i.getProductId(), i.getVariantId(), i.getProductName(), i.getVariantName(),
                i.getProductImageUrl(), i.getPrice(), i.getRegularPrice(), i.getListPrice(),
                i.getQuantity(), i.getLineTotal(),
                i.getId(), i.getCancelledQuantity(), i.remainingQuantity());
    }
}
