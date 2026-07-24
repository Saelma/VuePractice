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

    /** 주문의 품목을 상품 단위로 합산한다(옵션이 여러 개여도 상품 하나로 묶임). */
    public static List<SoldLine> from(Order order) {
        return order.getItems().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        OrderItem::getProductId,
                        java.util.stream.Collectors.summingLong(OrderItem::getQuantity)))
                .entrySet().stream()
                .map(e -> new SoldLine(e.getKey(), e.getValue()))
                .toList();
    }
}
