package com.glassvue.domain.order.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 주문 상태 머신(ORDERED→PAID→SHIPPED, 취소 규칙)의 순수 단위 테스트. */
class OrderTest {

    private Order newOrder() {
        return Order.create(UUID.randomUUID(), "구매자닉",
                List.of(OrderItem.of(UUID.randomUUID(), "지바", null, 10_000, 2)));
    }

    @Test
    @DisplayName("생성 직후: ORDERED · 결제가능 · 취소가능 · 발송불가 · 합계 계산")
    void created() {
        Order o = newOrder();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.ORDERED);
        assertThat(o.isPayable()).isTrue();
        assertThat(o.isCancellable()).isTrue();
        assertThat(o.isShippable()).isFalse();
        assertThat(o.getTotalPrice()).isEqualTo(20_000);
        assertThat(o.getPaidAt()).isNull();
    }

    @Test
    @DisplayName("결제: PAID로 전이 · paidAt 기록 · 발송가능 · 재결제불가 · 여전히 취소가능")
    void pay() {
        Order o = newOrder();
        o.pay();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(o.getPaidAt()).isNotNull();
        assertThat(o.isShippable()).isTrue();
        assertThat(o.isPayable()).isFalse();
        assertThat(o.isCancellable()).isTrue();
    }

    @Test
    @DisplayName("발송: SHIPPED로 전이 · shippedAt 기록 · 더는 취소 불가")
    void ship() {
        Order o = newOrder();
        o.pay();
        o.ship();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(o.getShippedAt()).isNotNull();
        assertThat(o.isCancellable()).isFalse();
        assertThat(o.isShippable()).isFalse();
    }

    @Test
    @DisplayName("취소: CANCELLED로 전이 · 이후 결제·취소 불가")
    void cancel() {
        Order o = newOrder();
        o.cancel();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(o.isCancellable()).isFalse();
        assertThat(o.isPayable()).isFalse();
    }

    @Test
    @DisplayName("소유권 판별")
    void ownership() {
        UUID me = UUID.randomUUID();
        Order o = Order.create(me, "구매자닉", List.of(OrderItem.of(UUID.randomUUID(), "x", null, 1000, 1)));
        assertThat(o.isOwnedBy(me)).isTrue();
        assertThat(o.isOwnedBy(UUID.randomUUID())).isFalse();
    }
}
