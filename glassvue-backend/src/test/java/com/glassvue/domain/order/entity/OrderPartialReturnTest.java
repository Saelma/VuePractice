package com.glassvue.domain.order.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 🔴 <b>부분 반품의 정산 배분</b> (2026-08-25, BACKLOG G-10) — 순수 단위 테스트.
 *
 * <p><b>표본을 G-4(부분 취소)와 «같은 주문»으로 잡았다</b> — A 20,000 · B 15,000 / 쿠폰 5,000 /
 * 적립금 2,000 / 배송비 무료 → 결제 28,000. 🔴 <b>그래야 두 흐름이 갈리는 자리가 숫자로 보인다</b>:
 * 같은 B 를 빼도 <b>취소는 12,001원, 반품은 12,858원</b>이다. 차이 857 이 «적립금 몫» 이고,
 * 반품은 그것을 환불액 <b>안에</b> 담아 돌려주기 때문이다({@code PointService} 두 메서드의 차이).
 *
 * <p><b>여기서 고정하는 것은 셋이다</b>:
 * <ol>
 *   <li>🔴 <b>전액 수렴</b> — 전량을 나눠 반품하면 Σ환불 = {@code refundableAmount()} 처음 값,
 *       Σ적립회수 = {@code earnedPoint}, Σ등급차감 = {@code rewardableAmount()} 처음 값.
 *       즉 <b>지금의 전체 반품과 글자 그대로 같은 값</b>이 된다 — 이 설계의 안전 조건이다.</li>
 *   <li>⚠ <b>미리 보기와 확정이 같은 값</b> — 둘이 갈리면 «누르기 전과 후가 다른» 화면이 된다.</li>
 *   <li>🔴 <b>취소 뒤 반품</b> — 한 주문에 둘이 겹쳐도 «남은 것» 기준이 무너지지 않는다.
 *       WA §1-2-1 이 지목한 자리가 바로 여기다.</li>
 * </ol>
 *
 * <p>⚠ <b>규칙의 원본은 BACKLOG G-10 이다.</b> 여기 다시 적지 않는다(CLAUDE.md).
 */
class OrderPartialReturnTest {

    /** 표본은 G-4 검산과 **같은 주문**이다 — 두 흐름의 숫자를 나란히 놓고 보려고 일부러 맞췄다. */
    private Order order(long couponDiscount, long usedPoint, long shippingFee, long... unitPrices) {
        List<OrderItem> items = new ArrayList<>();
        for (long p : unitPrices) {
            items.add(OrderItem.of(UUID.randomUUID(), UUID.randomUUID(), null, "상품" + p, null,
                    p, p, null, 1));
        }
        return Order.create(UUID.randomUUID(), "구매자닉", items,
                "수령인", "010-1234-5678", "06134", "서울시 강남구 테헤란로 1", "3층", null,
                shippingFee, "20260825-0001", couponDiscount > 0 ? "쿠폰" : null,
                couponDiscount, couponDiscount > 0 ? UUID.randomUUID() : null, usedPoint);
    }

    /** 배송완료까지 밀고 적립을 스냅샷한다 — 반품은 그 적립을 회수하므로 값이 있어야 의미가 있다. */
    private Order delivered(long earned, long couponDiscount, long usedPoint, long... unitPrices) {
        Order o = order(couponDiscount, usedPoint, 0, unitPrices);
        o.pay();
        o.ship(DeliveryCarrier.CJ, "123");
        o.deliver();
        o.recordEarnedPoint(earned);
        return o;
    }

    private OrderItem itemAt(Order o, int index) {
        return o.getItems().get(index);
    }

    private void request(Order o, int index, long qty) {
        o.requestReturn("ZZ-사유", Map.of(itemAt(o, index).getId(), qty));
    }

    // ── 🔴 전액 수렴 — 이 테스트 하나가 배분식 전체를 떠받친다 ──────────────

    @Test
    @DisplayName("🔴 G-10 검산 — B 반품 12,858원 · A 반품 17,142원 · 합계가 refundableAmount 30,000과 같다")
    void convergesOnTheDocumentedSample() {
        Order o = delivered(280, 5_000, 2_000, 20_000, 15_000);
        assertThat(o.refundableAmount()).isEqualTo(30_000);   // 35,000 − 5,000 (배송비는 안 돌려준다)
        assertThat(o.rewardableAmount()).isEqualTo(28_000);   // 35,000 − 5,000 − 2,000

        // ── 1회차: B(15,000). 쿠폰 몫 5000×15000/35000 = 2142.8 → 2142
        //           적립금 몫 2000×15000/35000 = 857.1 → 857 · 등급 몫 15000−2142−857 = 12,001
        //           적립 회수 280×12001/28000 = 120.01 → 120
        request(o, 1, 1);
        ReturnSettlement first = o.applyRequestedReturns();

        // 🔴 **취소였다면 12,001 이다**(G-4 검산). 반품은 적립금 몫 857 을 환불액 안에 담아 준다.
        assertThat(first.refundAmount()).isEqualTo(12_858);
        assertThat(first.earnedToReverse()).isEqualTo(120);
        assertThat(first.purchaseToRemove()).isEqualTo(12_001);
        assertThat(o.getReturnedCouponDiscount()).isEqualTo(2_142);
        assertThat(o.getReturnedPoint()).isEqualTo(857);
        // 남은 주문이 스스로 말이 되어야 한다 — 화면이 이 값을 그대로 그린다.
        assertThat(o.remainingItemsTotal()).isEqualTo(20_000);
        assertThat(o.remainingCouponDiscount()).isEqualTo(2_858);
        assertThat(o.remainingUsedPoint()).isEqualTo(1_143);
        assertThat(o.remainingEarnedPoint()).isEqualTo(160);
        // ⚠ 남은 것이 있으므로 **배송완료로 되돌아간다** — 다시 요청할 수 있어야 한다.
        assertThat(o.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(o.isReturnRequestable()).isTrue();

        // ── 2회차: A(20,000). 마지막이라 분모와 분자가 같아져 **남은 몫이 전부** 넘어간다.
        request(o, 0, 1);
        ReturnSettlement second = o.applyRequestedReturns();
        assertThat(second.refundAmount()).isEqualTo(17_142);
        assertThat(second.earnedToReverse()).isEqualTo(160);
        assertThat(second.purchaseToRemove()).isEqualTo(15_999);

        // 🔴 **수렴** — 셋 다 처음 값과 정확히 같다. 하나라도 어긋나면 잔돈이 새고 있다는 뜻이다.
        assertThat(first.refundAmount() + second.refundAmount()).isEqualTo(30_000);
        assertThat(first.earnedToReverse() + second.earnedToReverse()).isEqualTo(280);
        assertThat(first.purchaseToRemove() + second.purchaseToRemove()).isEqualTo(28_000);

        assertThat(o.getStatus()).isEqualTo(OrderStatus.RETURNED);
        assertThat(o.hasNothingLeft()).isTrue();
        assertThat(o.isReturnRequestable()).isFalse();
    }

    @Test
    @DisplayName("🔴 **한 회차에 두 품목** — 회차 안에서도 «이미 뗀 몫» 을 빼 가며 계산한다")
    void twoItemsInOneRound() {
        // 🔴 **이 테스트는 변형 주입이 찾아낸 구멍이다** (2026-08-25). 나머지 테스트가 전부
        //    «한 회차에 품목 하나» 라서, 분모에서 회차 누적(dItems)을 빼는 줄을 지워도
        //    **아무도 안 빨개졌다.** 그런데 실제 흐름은 고객이 여러 품목을 한 번에 고르는 것이다.
        Order o = delivered(280, 5_000, 2_000, 20_000, 15_000);
        o.requestReturn("ZZ-사유", Map.of(itemAt(o, 0).getId(), 1L, itemAt(o, 1).getId(), 1L));

        ReturnSettlement s = o.applyRequestedReturns();

        // ⚠ **한 회차 안에서도 분모가 줄어든다**: A 는 35,000 을, B 는 남은 15,000 을 분모로 쓴다.
        //    A: 쿠폰 5000×20000/35000 = 2857.1 → 2857 · 적립금 2000×20000/35000 = 1142.8 → 1142
        //    B: 쿠폰 2143×15000/15000 = 2143   · 적립금 858×15000/15000 = 858 (남은 몫이 전부 넘어간다)
        assertThat(o.getReturnedCouponDiscount()).isEqualTo(5_000);
        assertThat(o.getReturnedPoint()).isEqualTo(2_000);

        // 🔴 합계는 회차를 어떻게 쪼개든 같다 — 전액 수렴이 구조로 보장된다는 주장의 핵심이다.
        assertThat(s.refundAmount()).isEqualTo(30_000);      // 17,143 + 12,857
        assertThat(s.earnedToReverse()).isEqualTo(280);      // 160 + 120
        assertThat(s.purchaseToRemove()).isEqualTo(28_000);  // 16,001 + 11,999
        assertThat(o.getStatus()).isEqualTo(OrderStatus.RETURNED);

        // ⚠ **품목별 몫은 순서에 따라 다르다**(내림이라 경로 의존이다). 두 회차로 나눠 B 를 먼저
        //    빼면 B 가 2,142/857 을 가져가는데, 한 회차로 A 부터 돌면 B 가 2,143/858 을 가져간다.
        //    🔴 합계만 같고 자리는 다르다 — 그래서 회수 몫을 **저장**한다(유도할 수 없는 값이다).
        Order twoRounds = delivered(280, 5_000, 2_000, 20_000, 15_000);
        request(twoRounds, 1, 1);
        twoRounds.applyRequestedReturns();
        assertThat(twoRounds.getReturnedCouponDiscount()).isEqualTo(2_142);
        assertThat(twoRounds.getReturnedPoint()).isEqualTo(857);
    }

    @Test
    @DisplayName("🔴 잔돈이 가장 고약한 경우 — 1,000원을 10,000원짜리 셋에 나눠도 합이 정확히 1,000")
    void remainderIsAbsorbedByTheLastItem() {
        Order o = delivered(0, 1_000, 0, 10_000, 10_000, 10_000);

        long refund = 0;
        for (int i = 0; i < 3; i++) {
            request(o, i, 1);
            refund += o.applyRequestedReturns().refundAmount();
        }
        // 회수된 쿠폰 몫 333 · 333 · 334 → 합 1,000. 환불 합계 30,000 − 1,000 = 29,000.
        assertThat(o.getReturnedCouponDiscount()).isEqualTo(1_000);
        assertThat(refund).isEqualTo(29_000);
        assertThat(o.remainingCouponDiscount()).isZero();
    }

    @Test
    @DisplayName("🔴 한 품목 3개를 세 번에 나눠 반품 — 8,334 · 8,333 · 8,333 (합 25,000)")
    void sameItemAcrossThreeRounds() {
        // ⚠ 회차마다 **분모가 줄어** 쿠폰 몫이 1,666 → 1,667 → 1,667 로 갈린다.
        //    화면 미리보기가 이 값을 그대로 내야 한다(뷰 테스트가 8,333 을 단언한다).
        Order o = delivered(0, 5_000, 0, 10_000);
        // 위 헬퍼는 품목 하나에 수량 1 이라 수량 3 짜리를 직접 만든다.
        Order three = Order.create(UUID.randomUUID(), "구매자닉",
                List.of(OrderItem.of(UUID.randomUUID(), UUID.randomUUID(), null, "지바", null,
                        10_000, 10_000L, null, 3)),
                "수령인", "010-1234-5678", "06134", "서울시 강남구 테헤란로 1", "3층", null,
                0, "20260825-0003", "쿠폰", 5_000L, UUID.randomUUID(), 0L);
        three.pay();
        three.ship(DeliveryCarrier.CJ, "123");
        three.deliver();
        assertThat(o.getOrderNo()).isNotEqualTo(three.getOrderNo());   // 표본이 섞이지 않았다

        request(three, 0, 1);
        assertThat(three.applyRequestedReturns().refundAmount()).isEqualTo(8_334);
        assertThat(three.getReturnedCouponDiscount()).isEqualTo(1_666);

        // 🔴 **여기가 화면 미리보기와 맞물리는 자리다** — 두 번째 회차의 분모는 20,000 이다.
        request(three, 0, 1);
        assertThat(three.previewRequestedReturns().refundAmount()).isEqualTo(8_333);
        assertThat(three.applyRequestedReturns().refundAmount()).isEqualTo(8_333);

        request(three, 0, 1);
        assertThat(three.applyRequestedReturns().refundAmount()).isEqualTo(8_333);
        assertThat(three.getReturnedCouponDiscount()).isEqualTo(5_000);   // 수렴
        assertThat(three.getStatus()).isEqualTo(OrderStatus.RETURNED);
    }

    // ── ⚠ 미리 보기 = 확정 ──────────────────────────────────────────────

    @Test
    @DisplayName("⚠ 미리 보기와 확정이 같은 값이고, 미리 보기는 **아무것도 안 바꾼다**")
    void previewMatchesApplyAndMutatesNothing() {
        Order o = delivered(280, 5_000, 2_000, 20_000, 15_000);
        request(o, 1, 1);

        ReturnSettlement preview = o.previewRequestedReturns();
        // 🔴 두 번 불러도 값이 같아야 한다 — 상태를 건드렸다면 여기서 갈린다.
        assertThat(o.previewRequestedReturns()).isEqualTo(preview);
        assertThat(o.getReturnedItemsTotal()).isZero();
        assertThat(itemAt(o, 1).getReturnedQuantity()).isZero();
        assertThat(itemAt(o, 1).getReturnRequestedQuantity()).isEqualTo(1);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.RETURN_REQUESTED);

        assertThat(o.applyRequestedReturns()).isEqualTo(preview);
    }

    // ── 🔴 취소와 반품이 한 주문에서 겹칠 때 (WA §1-2-1 이 지목한 자리) ──────

    @Test
    @DisplayName("🔴 부분 취소 뒤 부분 반품 — 「남은 것」 기준이 무너지지 않는다 (합쳐서 원본을 못 넘는다)")
    void partialCancelThenPartialReturn() {
        // 한 품목 3개짜리 주문. 쿠폰·적립금 없이 배분을 단순하게 두고 **수량 경계**만 본다.
        Order o = Order.create(UUID.randomUUID(), "구매자닉",
                List.of(OrderItem.of(UUID.randomUUID(), UUID.randomUUID(), null, "지바", null,
                        10_000, 10_000L, null, 3)),
                "수령인", "010-1234-5678", "06134", "서울시 강남구 테헤란로 1", "3층", null,
                0, "20260825-0002", null, 0L, null, 0L);
        o.pay();
        o.cancelItem(itemAt(o, 0), 1);          // 3개 중 1개 취소 → 남은 2개
        assertThat(itemAt(o, 0).remainingQuantity()).isEqualTo(2);

        o.ship(DeliveryCarrier.CJ, "123");
        o.deliver();
        o.recordEarnedPoint(200);               // 적립 기준액 20,000 에 대한 적립

        request(o, 0, 1);                        // 남은 2개 중 1개 반품
        ReturnSettlement s = o.applyRequestedReturns();

        assertThat(s.refundAmount()).isEqualTo(10_000);
        assertThat(s.purchaseToRemove()).isEqualTo(10_000);
        assertThat(s.earnedToReverse()).isEqualTo(100);      // 200 × 10,000 / 20,000
        // 🔴 수량 셋이 **합쳐서** 원본을 넘지 않는다 — DB CHECK 가 마지막으로 잡는 그 불변식이다.
        assertThat(itemAt(o, 0).getCancelledQuantity()).isEqualTo(1);
        assertThat(itemAt(o, 0).getReturnedQuantity()).isEqualTo(1);
        assertThat(itemAt(o, 0).remainingQuantity()).isEqualTo(1);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.DELIVERED);

        // 마지막 1개까지 반품하면 주문이 통째로 끝난다.
        request(o, 0, 1);
        o.applyRequestedReturns();
        assertThat(o.hasNothingLeft()).isTrue();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.RETURNED);
        // ⚠ 🔴 **배송비 환불 스위치는 여전히 «취소만» 이다**(G-10 결정 3) — 반품으로 비워졌으므로 거짓이다.
        assertThat(o.isFullyCancelledByItems()).isFalse();
    }

    @Test
    @DisplayName("🔴 배송비: 반품으로 비워진 주문은 «배송비만» 남는다 (취소로 비워지면 0)")
    void shippingFeeIsNotRefundedOnReturn() {
        Order returned = delivered(0, 0, 0, 10_000);
        request(returned, 0, 1);
        returned.applyRequestedReturns();
        // ⚠ 이 표본은 배송비 0 이라 값이 안 갈린다 — 배송비가 붙은 주문으로 다시 본다.
        assertThat(returned.getPayAmount()).isZero();

        Order o = order(0, 0, 3_000, 10_000);
        o.pay();
        o.ship(DeliveryCarrier.CJ, "123");
        o.deliver();
        request(o, 0, 1);
        o.applyRequestedReturns();
        // 🔴 반품은 물건이 이미 나갔다 — 운임이 실제로 발생했으므로 고객이 낸 것은 배송비뿐이다.
        assertThat(o.getPayAmount()).isEqualTo(3_000);
        assertThat(o.returnRefundedAmount()).isEqualTo(10_000);

        // ⚠ 대조군: 같은 주문을 **취소**로 비우면 배송비를 돌려주므로 0 이다.
        Order cancelled = order(0, 0, 3_000, 10_000);
        cancelled.pay();
        cancelled.cancelItem(itemAt(cancelled, 0), 1);
        assertThat(cancelled.getPayAmount()).isZero();
        assertThat(cancelled.refundedAmount()).isEqualTo(13_000);   // 상품 10,000 + 배송비 3,000
    }

    // ── ⚠ 요청·거절이 남기는 것 ─────────────────────────────────────────

    @Test
    @DisplayName("⚠ 거절하면 요청 수량이 지워진다 — 안 지우면 다음 요청에 «안 고른 품목» 이 따라간다")
    void rejectClearsRequestedQuantities() {
        Order o = delivered(0, 0, 0, 20_000, 15_000);
        o.requestReturn("ZZ-첫요청", Map.of(itemAt(o, 0).getId(), 1L, itemAt(o, 1).getId(), 1L));
        assertThat(itemAt(o, 0).getReturnRequestedQuantity()).isEqualTo(1);

        o.rejectReturn("ZZ-거절");
        assertThat(itemAt(o, 0).getReturnRequestedQuantity()).isZero();
        assertThat(itemAt(o, 1).getReturnRequestedQuantity()).isZero();

        // 🔴 다시 요청할 때 **B 만** 골랐는데 A 가 따라가면 안 된다.
        request(o, 1, 1);
        assertThat(itemAt(o, 0).getReturnRequestedQuantity()).isZero();
        assertThat(itemAt(o, 1).getReturnRequestedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("⚠ 재요청도 «덮어쓴다» — 이전 요청이 누적되지 않는다")
    void reRequestOverwrites() {
        Order o = delivered(0, 0, 0, 10_000);
        o.requestReturn("ZZ-1", Map.of(itemAt(o, 0).getId(), 1L));
        o.requestReturn("ZZ-2", Map.of(itemAt(o, 0).getId(), 1L));
        assertThat(itemAt(o, 0).getReturnRequestedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("🔴 남은 수량을 넘겨 요청할 수 없다 — 넘기면 재고와 돈이 두 번 돌아간다")
    void cannotRequestMoreThanRemaining() {
        Order o = delivered(0, 0, 0, 10_000);
        UUID itemId = itemAt(o, 0).getId();
        assertThatThrownBy(() -> o.requestReturn("ZZ-사유", Map.of(itemId, 2L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("⚠ 적립 기준액이 0 인 주문(쿠폰·적립금으로 전액 결제) — 회수할 적립이 없고 0 나눗셈도 안 난다")
    void zeroRewardBaseIsSafe() {
        // 10,000 짜리 하나를 쿠폰 6,000 + 적립금 4,000 으로 전액 결제 → rewardableAmount 0
        Order o = delivered(0, 6_000, 4_000, 10_000);
        assertThat(o.rewardableAmount()).isZero();

        request(o, 0, 1);
        ReturnSettlement s = o.applyRequestedReturns();
        assertThat(s.earnedToReverse()).isZero();
        assertThat(s.purchaseToRemove()).isZero();
        assertThat(s.refundAmount()).isEqualTo(4_000);   // 10,000 − 쿠폰 몫 6,000
    }
}
