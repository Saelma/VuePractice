package com.glassvue.domain.order.event;

import com.glassvue.domain.order.entity.Order;
import com.glassvue.domain.order.entity.OrderItem;
import java.util.List;
import java.util.UUID;

/**
 * 주문 이벤트에 실리는 "어떤 상품이 몇 개" 한 줄 (2026-07-24, B-8).
 *
 * <p>판매량 비정규화({@code product.sold_count})를 위해 catalog 가 상품별 증감을 알아야 하는데,
 * catalog 는 order_item 을 조회할 수 없다(도메인 경계). 그래서 이벤트가 상품·수량을 실어 나른다 —
 * 이벤트가 도메인 간 공개 계약이라는 원칙 그대로다(리뷰 별점이 productId·평점을 싣는 것과 같다).
 *
 * <p>재고 복원은 옵션(variant) 단위지만, 판매량은 <b>상품 단위</b>다. 같은 상품의 여러 옵션을 함께 사면
 * 한 상품에 수량이 합쳐져야 인기순이 맞으므로 여기서 productId 로 합산해 만든다.
 */
public record SoldLine(UUID productId, long quantity) {

    /**
     * 주문의 품목을 <b>원본 수량</b>으로 합산한다 — <b>주문 시점 전용</b>이다({@code OrderPlacedEvent}).
     *
     * <p>🔴 <b>되돌릴 때는 쓰면 안 된다</b> (2026-08-25, G-10에서 발견). 주문 시점에는
     * «원본 = 남은 것» 이라 구별할 이유가 없지만, 부분 취소·부분 반품이 생기면 <b>이미 되돌린 몫을
     * 또 되돌린다.</b> 되돌리는 쪽은 {@link #remaining(Order)} 나 {@link #of(OrderItem, long)} 을 쓴다.
     * ⚠ WA §1-2-1 이 «읽는 값이 원본인지 남은 것인지 대조하라» 는 자리가 정확히 여기였다.
     */
    public static List<SoldLine> ordered(Order order) {
        return sum(order, OrderItem::getQuantity);
    }

    /**
     * 주문에 <b>아직 살아 있는</b> 수량으로 합산한다 — 전체 취소·전체 반품이 되돌릴 양이다.
     *
     * <p>부분 취소·부분 반품이 이미 자기 몫을 되돌렸으므로, 남은 것만 빼야 «+주문 / −되돌림» 이
     * 정확히 상쇄된다. 부분이 한 번도 없었으면 원본과 같은 값이다.
     */
    public static List<SoldLine> remaining(Order order) {
        return sum(order, OrderItem::remainingQuantity);
    }

    /**
     * <b>지금 반품 «요청된»</b> 수량으로 합산한다 (G-10).
     *
     * <p>⚠ <b>반드시 승인 정산 «전»에 불러야 한다</b> — {@code applyRequestedReturns()} 가 요청 수량을
     * {@code returnedQuantity} 로 옮기면서 0 으로 지우기 때문이다. 정산 뒤에 부르면 빈 목록이 나오고,
     * <b>판매량이 조용히 안 줄어든다.</b>
     */
    public static List<SoldLine> ofRequestedReturn(Order order) {
        return sum(order, OrderItem::getReturnRequestedQuantity);
    }

    /** 품목 하나에서 {@code qty} 개만 되돌린다 — 부분 취소 한 회차의 몫. */
    public static List<SoldLine> of(OrderItem item, long qty) {
        return qty <= 0 ? List.of() : List.of(new SoldLine(item.getProductId(), qty));
    }

    private static List<SoldLine> sum(Order order, java.util.function.ToLongFunction<OrderItem> qty) {
        return order.getItems().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        OrderItem::getProductId,
                        java.util.stream.Collectors.summingLong(qty)))
                .entrySet().stream()
                // ⚠ 0 줄은 버린다 — 「갱신 대상 없음」 로그만 남기고 아무 일도 안 하는 줄이다.
                .filter(e -> e.getValue() != 0)
                .map(e -> new SoldLine(e.getKey(), e.getValue()))
                .toList();
    }
}
