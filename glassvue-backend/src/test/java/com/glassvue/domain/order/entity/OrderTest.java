package com.glassvue.domain.order.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 주문 상태 머신(ORDERED→PAID→SHIPPED→DELIVERED, 취소 규칙)의 순수 단위 테스트. */
class OrderTest {

    private Order newOrder() {
        return Order.create(UUID.randomUUID(), "구매자닉",
                List.of(OrderItem.of(UUID.randomUUID(), UUID.randomUUID(), null, "지바", null, 10_000, null, 2)),
                "수령인", "010-1234-5678", "06134", "서울시 강남구 테헤란로 1", "3층", null, 3_000, "20260101-0001", null, 0L, 0L);
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
        // totalPrice는 상품 합계, payAmount는 실제 결제 금액(= 합계 + 배송비).
        // 둘을 갈라 두지 않으면 과거 주문의 숫자가 무엇인지 알 수 없어진다.
        assertThat(o.getShippingFee()).isEqualTo(3_000);
        assertThat(o.getPayAmount()).isEqualTo(23_000);
    }

    @Test
    @DisplayName("배송비 0(무료배송)이면 결제 금액은 상품 합계와 같다")
    void freeShipping() {
        Order o = Order.create(UUID.randomUUID(), "구매자닉",
                List.of(OrderItem.of(UUID.randomUUID(), UUID.randomUUID(), null, "지바", null, 40_000, null, 1)),
                "수령인", "010-1234-5678", "06134", "서울시 강남구 테헤란로 1", "3층", null, 0, "20260101-0002", null, 0L, 0L);
        assertThat(o.getShippingFee()).isZero();
        assertThat(o.getPayAmount()).isEqualTo(o.getTotalPrice());
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
    @DisplayName("발송: SHIPPED로 전이 · shippedAt·운송장 기록 · 더는 취소 불가 · 배송완료 가능")
    void ship() {
        Order o = newOrder();
        o.pay();
        o.ship(DeliveryCarrier.CJ, "123456789012");
        assertThat(o.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(o.getShippedAt()).isNotNull();
        assertThat(o.isCancellable()).isFalse();
        assertThat(o.isShippable()).isFalse();
        // 운송장은 발송과 한 몸이다 — 발송했는데 추적 정보가 없는 상태를 만들지 않는다.
        assertThat(o.getShipCarrier()).isEqualTo(DeliveryCarrier.CJ);
        assertThat(o.getShipTrackingNo()).isEqualTo("123456789012");
        assertThat(o.isDeliverable()).isTrue();
    }

    @Test
    @DisplayName("배송완료: DELIVERED로 전이 · deliveredAt 기록 · 재처리 불가 · 취소 불가")
    void deliver() {
        Order o = newOrder();
        o.pay();
        o.ship(DeliveryCarrier.CJ, "123456789012");
        o.deliver();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(o.getDeliveredAt()).isNotNull();
        assertThat(o.isDeliverable()).isFalse();
        assertThat(o.isCancellable()).isFalse();
        // 운송장은 그대로 남는다 — 수령 후에도 배송 이력은 조회할 수 있어야 한다.
        assertThat(o.getShipTrackingNo()).isEqualTo("123456789012");
    }

    @Test
    @DisplayName("발송 전에는 배송완료 처리할 수 없다 (ORDERED·PAID)")
    void notDeliverableBeforeShip() {
        Order o = newOrder();
        assertThat(o.isDeliverable()).isFalse();
        o.pay();
        assertThat(o.isDeliverable()).isFalse();
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
        Order o = Order.create(me, "구매자닉", List.of(OrderItem.of(UUID.randomUUID(), UUID.randomUUID(), null, "x", null, 1000, null, 1)),
                "수령인", "010-1234-5678", "06134", "서울시 강남구 테헤란로 1", "3층", null, 3_000, "20260101-0001", null, 0L, 0L);
        assertThat(o.isOwnedBy(me)).isTrue();
        assertThat(o.isOwnedBy(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("쿠폰: 결제 금액 = 상품합계 − 쿠폰할인 + 배송비 (배송비는 할인 전 기준이라 그대로)")
    void payAmountWithCoupon() {
        // 상품합계 20,000 / 배송비 3,000 / 쿠폰 5,000 할인
        Order o = Order.create(UUID.randomUUID(), "구매자닉",
                List.of(OrderItem.of(UUID.randomUUID(), UUID.randomUUID(), null, "지바", null, 10_000, null, 2)),
                "수령인", "010-1234-5678", "06134", "서울시 강남구 테헤란로 1", "3층", null,
                3_000, "20260101-0003", "5천원 쿠폰", 5_000L, 0L);

        assertThat(o.getTotalPrice()).isEqualTo(20_000);   // 상품합계는 할인 전 그대로
        assertThat(o.getCouponDiscount()).isEqualTo(5_000);
        assertThat(o.getShippingFee()).isEqualTo(3_000);
        assertThat(o.getPayAmount()).isEqualTo(18_000);    // 20,000 - 5,000 + 3,000
        assertThat(o.getCouponName()).isEqualTo("5천원 쿠폰");
    }

    @Test
    @DisplayName("쿠폰 미사용: 할인액 0이고 쿠폰명은 null — 결제 금액은 상품합계 + 배송비")
    void payAmountWithoutCoupon() {
        Order o = newOrder();
        assertThat(o.getCouponDiscount()).isZero();
        assertThat(o.getCouponName()).isNull();
        assertThat(o.getPayAmount()).isEqualTo(o.getTotalPrice() + o.getShippingFee());
    }

}
