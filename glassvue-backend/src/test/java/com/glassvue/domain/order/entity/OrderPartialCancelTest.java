package com.glassvue.domain.order.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 🔴 <b>부분 취소의 정산 배분</b> (2026-08-24, BACKLOG G-4) — 순수 단위 테스트.
 *
 * <p><b>여기서 고정하는 것은 하나다: 전액 수렴.</b> 품목을 하나씩 다 취소하면 환불 합계가
 * <b>정확히 결제금액</b>이어야 한다. 배분이 내림이라 회차마다 1원이 버려지는데, 그 잔돈이
 * 주문에 남아 다음 회차로 따라가지 않으면 <b>여기서 어긋난다.</b>
 *
 * <p>🔴 <b>이 어긋남은 화면이 멀쩡히 도는 채로 일어난다</b> — 각 회차의 환불액은 그럴듯하고,
 * 합계를 세어 보기 전에는 아무도 모른다. 그게 이 테스트가 있어야 하는 이유다.
 *
 * <p>⚠ <b>규칙의 원본은 BACKLOG G-4 「결정 (2026-08-24, 사용자 확정)」이다.</b> 여기 다시 적지 않는다 —
 * 같은 사실을 두 곳에 적으면 한쪽만 고쳐진다(CLAUDE.md).
 */
class OrderPartialCancelTest {

    /** 표본은 G-4 검산과 같다 — A 20,000 · B 15,000 / 쿠폰 5,000 / 적립금 2,000 / 배송비 무료. */
    private Order order(long couponDiscount, long usedPoint, long shippingFee, long... unitPrices) {
        List<OrderItem> items = new ArrayList<>();
        for (long p : unitPrices) {
            items.add(OrderItem.of(UUID.randomUUID(), UUID.randomUUID(), null, "상품" + p, null,
                    p, p, null, 1));
        }
        return Order.create(UUID.randomUUID(), "구매자닉", items,
                "수령인", "010-1234-5678", "06134", "서울시 강남구 테헤란로 1", "3층", null,
                shippingFee, "20260824-0001", couponDiscount > 0 ? "쿠폰" : null,
                couponDiscount, couponDiscount > 0 ? UUID.randomUUID() : null, usedPoint);
    }

    private OrderItem itemAt(Order o, int index) {
        return o.getItems().get(index);
    }

    // ── 🔴 전액 수렴 — 이 테스트 하나가 배분식 전체를 떠받친다 ──────────────

    @Test
    @DisplayName("🔴 G-4 검산 표본 — B 취소 12,001원 · A 취소 15,999원 · 합계가 결제금액 28,000과 같다")
    void convergesOnTheDocumentedSample() {
        Order o = order(5_000, 2_000, 0, 20_000, 15_000);
        assertThat(o.getPayAmount()).isEqualTo(28_000);

        // B(15,000) 취소 — 쿠폰 몫 5000×15000/35000 = 2142.85 → 2142, 적립금 몫 2000×15000/35000 = 857.1 → 857
        long refundB = o.cancelItem(itemAt(o, 1), 1);
        assertThat(refundB).isEqualTo(12_001);
        assertThat(o.getCancelledCouponDiscount()).isEqualTo(2_142);
        assertThat(o.getCancelledPoint()).isEqualTo(857);

        // 남은 주문이 스스로 말이 되어야 한다 — 화면이 이 값을 그대로 그린다.
        assertThat(o.remainingItemsTotal()).isEqualTo(20_000);
        assertThat(o.remainingCouponDiscount()).isEqualTo(2_858);
        assertThat(o.remainingUsedPoint()).isEqualTo(1_143);
        assertThat(o.getPayAmount()).isEqualTo(15_999);

        // A 취소 — 분모와 분자가 같아져 남은 몫이 **전부** 넘어간다.
        long refundA = o.cancelItem(itemAt(o, 0), 1);
        assertThat(refundA).isEqualTo(15_999);

        assertThat(refundB + refundA).isEqualTo(28_000);
        assertThat(o.getPayAmount()).isZero();
        assertThat(o.refundedAmount()).isEqualTo(28_000);
        assertThat(o.isFullyCancelledByItems()).isTrue();
    }

    @Test
    @DisplayName("🔴 잔돈이 나뉘지 않는 경우 — 1,000원을 셋에 나누면 333·333·334 로 마지막이 흡수한다")
    void remainderLandsOnTheLastItem() {
        Order o = order(1_000, 0, 0, 10_000, 10_000, 10_000);

        long r1 = o.cancelItem(itemAt(o, 0), 1);
        long r2 = o.cancelItem(itemAt(o, 1), 1);
        long r3 = o.cancelItem(itemAt(o, 2), 1);

        // 10,000 − 333 = 9,667 / 9,667 / 10,000 − 334 = 9,666
        assertThat(List.of(r1, r2, r3)).containsExactly(9_667L, 9_667L, 9_666L);
        assertThat(r1 + r2 + r3).isEqualTo(29_000); // = 30,000 − 1,000
        assertThat(o.getCancelledCouponDiscount()).isEqualTo(1_000);
    }

    @Test
    @DisplayName("🔴 어느 순서로 취소해도 합계는 같다 — 잔돈이 어디 남든 수렴한다")
    void convergesRegardlessOfOrder() {
        long[] prices = {7_777, 3_333, 11_111};
        long expected = 22_221 - 1_234 - 567; // 상품합계 − 쿠폰 − 적립금

        for (int[] seq : new int[][]{{0, 1, 2}, {2, 1, 0}, {1, 0, 2}, {1, 2, 0}}) {
            Order o = order(1_234, 567, 0, prices);
            long total = 0;
            for (int i : seq) {
                total += o.cancelItem(itemAt(o, i), 1);
            }
            assertThat(total).as("순서 %s", java.util.Arrays.toString(seq)).isEqualTo(expected);
            assertThat(o.getPayAmount()).isZero();
        }
    }

    // ── 수량 단위 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("한 품목의 수량을 나눠 취소해도 수렴한다 — 3개를 1개씩 세 번")
    void convergesAcrossQuantitySplits() {
        List<OrderItem> items = List.of(
                OrderItem.of(UUID.randomUUID(), UUID.randomUUID(), null, "3개짜리", null, 10_000, 10_000L, null, 3));
        Order o = Order.create(UUID.randomUUID(), "구매자닉", items,
                "수령인", "010-1234-5678", "06134", "주소", null, null,
                0, "20260824-0002", "쿠폰", 1_000, UUID.randomUUID(), 0);
        assertThat(o.getPayAmount()).isEqualTo(29_000);

        long total = 0;
        for (int i = 0; i < 3; i++) {
            total += o.cancelItem(itemAt(o, 0), 1);
        }
        assertThat(total).isEqualTo(29_000);
        assertThat(itemAt(o, 0).remainingQuantity()).isZero();
        assertThat(o.isFullyCancelledByItems()).isTrue();
    }

    @Test
    @DisplayName("🔴 원본 수량은 안 깎인다 — 「3개 중 1개 취소됨」을 그리려면 둘 다 있어야 한다")
    void keepsOriginalQuantity() {
        List<OrderItem> items = List.of(
                OrderItem.of(UUID.randomUUID(), UUID.randomUUID(), null, "3개짜리", null, 10_000, 10_000L, null, 3));
        Order o = Order.create(UUID.randomUUID(), "구매자닉", items,
                "수령인", "010-1234-5678", "06134", "주소", null, null,
                0, "20260824-0003", null, 0, null, 0);

        o.cancelItem(itemAt(o, 0), 1);

        assertThat(itemAt(o, 0).getQuantity()).isEqualTo(3);        // 원본 스냅샷
        assertThat(itemAt(o, 0).getCancelledQuantity()).isEqualTo(1);
        assertThat(itemAt(o, 0).remainingQuantity()).isEqualTo(2);
        assertThat(itemAt(o, 0).getLineTotal()).isEqualTo(30_000);  // 원본 스냅샷
        assertThat(itemAt(o, 0).remainingAmount()).isEqualTo(20_000);
        assertThat(o.getTotalPrice()).isEqualTo(30_000);            // 원본 스냅샷
        assertThat(o.remainingItemsTotal()).isEqualTo(20_000);
    }

    // ── 🔴 소급하지 않는 것들 (G-4 결정 1·2) ─────────────────────────────

    @Test
    @DisplayName("🔴 배송비는 부분 취소로 안 움직인다 — 「취소했더니 돈을 더 냈다」를 만들지 않는다")
    void shippingFeeNeverMoves() {
        // 35,000 이라 주문 시점엔 무료였다. B 를 빼면 20,000 이 되어 기준(30,000) 미달이지만 —
        Order o = order(0, 0, 0, 20_000, 15_000);
        o.cancelItem(itemAt(o, 1), 1);

        assertThat(o.getShippingFee()).isZero();
        assertThat(o.getPayAmount()).isEqualTo(20_000); // 3,000 이 새로 붙지 않았다
    }

    @Test
    @DisplayName("🔴 쿠폰 최소금액은 소급하지 않는다 — 남은 주문이 기준 미달이어도 할인이 그대로 걸려 있다")
    void couponMinOrderIsNotReapplied() {
        Order o = order(5_000, 0, 0, 20_000, 15_000);
        o.cancelItem(itemAt(o, 1), 1);

        // 남은 상품합계 20,000 은 「30,000 이상」 조건에 못 미치지만 할인은 회수되지 않았다.
        assertThat(o.remainingCouponDiscount()).isEqualTo(2_858);
        assertThat(o.getMemberCouponId()).isNotNull(); // 복구 대상으로 남아 있다(전량 취소 때 쓴다)
    }

    // ── 🔴 부분 취소 **뒤에** 오는 전체 취소 (2026-08-24 회귀) ─────────────

    @Test
    @DisplayName("🔴 부분 취소 뒤 전체 취소는 **남은 것만** 되돌릴 대상이다 — 원본으로 되돌리면 두 번 준다")
    void remainingIsWhatFullCancelMustGiveBack() {
        // 실측 사고(`20260824-5296`): 적립금 2,000 을 쓴 주문에서 부분 취소가 857·571 을 돌려준 뒤
        // 「주문 취소」가 **원본 2,000 을 또** 돌려줘 합계 3,428 이 나갔다. 재고도 2개 주문에 3개가 돌아갔다.
        Order o = order(5_000, 2_000, 0, 20_000, 15_000);
        o.cancelItem(itemAt(o, 1), 1);

        // 🔴 전체 취소가 읽어야 하는 값들 — 원본이 아니라 «남은 것» 이다.
        assertThat(o.remainingUsedPoint()).isEqualTo(1_143);   // 원본 2,000 이 아니다
        assertThat(itemAt(o, 1).remainingQuantity()).isZero(); // 이미 다 돌아갔다 — 또 넣으면 안 된다
        assertThat(itemAt(o, 0).remainingQuantity()).isEqualTo(1);

        // 되돌린 적립금(857) + 남은 것(1,143) = 쓴 것(2,000). 이 합이 안 맞으면 어딘가에서 새거나 겹친다.
        assertThat(o.getCancelledPoint() + o.remainingUsedPoint()).isEqualTo(2_000);
    }

    @Test
    @DisplayName("🔴 반품 환불액도 남은 것 기준이다 — PAID 에서 부분 취소한 뒤 발송·배송완료·반품이 가능하다")
    void refundableFollowsRemaining() {
        Order o = order(5_000, 0, 0, 20_000, 15_000);
        assertThat(o.refundableAmount()).isEqualTo(30_000); // 35,000 − 5,000

        o.cancelItem(itemAt(o, 1), 1);
        // 남은 20,000 − 남은 쿠폰 2,858 = 17,142. 원본으로 세면 30,000 을 또 돌려준다.
        assertThat(o.refundableAmount()).isEqualTo(17_142);
    }

    // ── 🔴 전량이 빠지면 배송비도 돌아간다 (2026-08-24 실측) ────────────────

    @Test
    @DisplayName("🔴 전량이 빠진 주문에 **배송비만 남지 않는다** — 「남은 결제 금액 3,000원」이 나왔었다")
    void shippingDoesNotLingerOnAnEmptiedOrder() {
        // 실측(`20260824-5297`): 25,000 주문(배송비 3,000)을 품목별로 다 뺐더니 payAmount 가 3,000 이었다.
        Order o = order(5_000, 2_000, 3_000, 15_000, 10_000);
        assertThat(o.getPayAmount()).isEqualTo(21_000); // 25,000 − 5,000 − 2,000 + 3,000

        long r1 = o.cancelItem(itemAt(o, 0), 1);
        long r2 = o.cancelItem(itemAt(o, 1), 1);

        assertThat(o.isFullyCancelledByItems()).isTrue();
        assertThat(o.getPayAmount()).isZero();
        // 돌려준 것 + 남은 것 = 처음 결제한 것. 배송비가 환불에 포함돼야 이 등식이 성립한다.
        assertThat(o.refundedAmount()).isEqualTo(21_000);
        assertThat(r1 + r2 + o.getShippingFee()).isEqualTo(21_000);
    }

    @Test
    @DisplayName("부분 취소를 한 적 없는 주문은 이 스위치에 안 걸린다 — 기존 취소 주문의 표시가 안 바뀐다")
    void untouchedOrderKeepsItsHistoricalAmount() {
        Order o = order(5_000, 2_000, 3_000, 15_000, 10_000);
        o.cancel(null);
        assertThat(o.isFullyCancelledByItems()).isFalse();
        assertThat(o.getPayAmount()).isEqualTo(21_000); // 예전처럼 «결제했던 금액»
    }

    // ── 적립 · 경계 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("적립 대상 금액도 남은 것 기준이다 — 돌려준 돈에 적립을 주지 않는다")
    void rewardableFollowsRemaining() {
        Order o = order(5_000, 2_000, 0, 20_000, 15_000);
        assertThat(o.rewardableAmount()).isEqualTo(28_000);

        o.cancelItem(itemAt(o, 1), 1);
        assertThat(o.rewardableAmount()).isEqualTo(15_999);
    }

    @Test
    @DisplayName("남은 수량을 넘겨 취소할 수 없다 — 넘기면 환불이 결제금액보다 커진다")
    void cannotCancelMoreThanRemaining() {
        Order o = order(0, 0, 0, 10_000, 10_000);

        assertThatThrownBy(() -> o.cancelItem(itemAt(o, 0), 2))
                .isInstanceOf(IllegalArgumentException.class);

        o.cancelItem(itemAt(o, 0), 1);
        // 이미 다 뺀 품목을 또 빼려 하면 — 주문에는 아직 다른 품목이 남아 있다.
        assertThatThrownBy(() -> o.cancelItem(itemAt(o, 0), 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("🔴 남은 품목이 0인 주문은 배분 자체가 성립하지 않는다 — 0으로 나누기 전에 막는다")
    void guardsAgainstEmptyRemainder() {
        Order o = order(0, 0, 0, 10_000);
        o.cancelItem(itemAt(o, 0), 1);

        // ⚠ **실제로는 여기 못 닿는다** — 서비스가 그 앞에서 두 번 막는다: 마지막 품목이 빠지면
        //    주문이 CANCELLED 로 떨어져 requireCancellable 이 걸고, 그 전에 남은 수량 0 이 걸린다.
        //    🔴 그래도 둔다 — 닿는다면 그건 배분식이 어긋났다는 신호이고, 그때 0으로 나누면
        //    **조용히 틀린 금액**이 나온다(ArithmeticException 도 아니다: 분자가 0이면 0/0 이 아니라 예외다).
        assertThatThrownBy(() -> o.cancelItem(itemAt(o, 0), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("남은 품목이 없는");
    }

    @Test
    @DisplayName("쿠폰·적립금이 없는 주문은 그냥 상품금액이 돌아간다")
    void plainOrderRefundsLineAmount() {
        Order o = order(0, 0, 3_000, 5_000, 5_000);
        assertThat(o.getPayAmount()).isEqualTo(13_000);

        assertThat(o.cancelItem(itemAt(o, 0), 1)).isEqualTo(5_000);
        assertThat(o.getPayAmount()).isEqualTo(8_000); // 5,000 + 배송비 3,000
    }

    @Test
    @DisplayName("부분 취소가 없으면 응답값이 예전과 똑같이 읽힌다 — 회수된 몫은 전부 0")
    void untouchedOrderReadsAsBefore() {
        Order o = order(5_000, 2_000, 3_000, 10_000);
        assertThat(o.getCancelledItemsTotal()).isZero();
        assertThat(o.getCancelledCouponDiscount()).isZero();
        assertThat(o.getCancelledPoint()).isZero();
        assertThat(o.refundedAmount()).isZero();
        assertThat(o.remainingItemsTotal()).isEqualTo(o.getTotalPrice());
        assertThat(o.getPayAmount()).isEqualTo(10_000 - 5_000 - 2_000 + 3_000);
        assertThat(o.isFullyCancelledByItems()).isFalse();
    }
}
