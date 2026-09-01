package com.glassvue.global.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 배송비 계산 규칙. 정책은 설정으로 바뀌지만 계산 자체는 여기 고정된다.
 *
 * <p>🔴 <b>2026-09-01: 기준 금액을 «안 받는» 형태가 사라졌다</b> — G-6 이 등급별 기준을 만든 뒤로
 * 그 형태는 <b>부르는 것만으로 등급이 조용히 무시되는</b> 자리가 됐고, 부르는 곳이 0 이던 참에 지웠다.
 * ⚠ 그래서 아래 테스트들은 «기본 기준» 을 <b>손으로 넘긴다</b>({@code BASE}) — 그게 이제
 * 호출자가 하는 일이고, <b>테스트도 같은 모양으로 부르는 것이 맞다.</b>
 */
class ShippingPolicyTest {

    /** 설정의 기본 무료배송 기준 — 이제 «부르는 쪽이 명시하는» 값이라 테스트도 그렇게 쓴다. */
    private static final long BASE = 30_000;

    private ShippingPolicy policy(long fee, long threshold) {
        ShippingPolicy p = new ShippingPolicy();
        p.setFee(fee);
        p.setFreeThreshold(threshold);
        return p;
    }

    @Test
    @DisplayName("기준 미만이면 배송비를 받는다")
    void chargesBelowThreshold() {
        ShippingPolicy p = policy(3000, 30000);
        assertThat(p.feeFor(29_999, BASE)).isEqualTo(3000);
        assertThat(p.feeFor(1, BASE)).isEqualTo(3000);
    }

    @Test
    @DisplayName("기준 이상이면 무료 — 경계값(정확히 기준)도 무료다")
    void freeAtOrAboveThreshold() {
        ShippingPolicy p = policy(3000, 30000);
        assertThat(p.feeFor(30_000, BASE)).isZero();   // 경계: '이상'이므로 무료
        assertThat(p.feeFor(30_001, BASE)).isZero();
    }

    @Test
    @DisplayName("빈 장바구니(0원)에는 배송비를 붙이지 않는다")
    void emptyCartHasNoFee() {
        // 붙이면 화면에 "0원 + 배송비 3,000원"이 떠서 담지도 않은 값을 청구하는 것처럼 보인다.
        ShippingPolicy p = policy(3000, 30000);
        assertThat(p.feeFor(0, BASE)).isZero();
        assertThat(p.feeFor(-1, BASE)).isZero();
    }

    @Test
    @DisplayName("무료 기준이 0이면 무료배송 없음 — 항상 배송비를 받는다")
    void zeroThresholdMeansNoFreeShipping() {
        ShippingPolicy p = policy(3000, 0);
        assertThat(p.feeFor(1_000_000, 0)).isEqualTo(3000);
    }

    @Test
    @DisplayName("무료배송까지 남은 금액 — 이미 무료면 0")
    void amountUntilFree() {
        ShippingPolicy p = policy(3000, 30000);
        assertThat(p.amountUntilFree(20_000, BASE)).isEqualTo(10_000);
        assertThat(p.amountUntilFree(30_000, BASE)).isZero();
        assertThat(p.amountUntilFree(0, BASE)).isZero();     // 빈 장바구니에 "N원 더" 안내는 안 띄운다
        assertThat(policy(3000, 0).amountUntilFree(10, 0)).isZero(); // 무료배송 정책 자체가 없음
    }

    // ─────────── 「적용할 기준 금액」을 받는 형태 (2026-08-28, BACKLOG G-6) ───────────

    @Test
    @DisplayName("기준 금액을 넘겨 받으면 그걸로 판정한다 — 설정값을 안 본다")
    void usesGivenThresholdNotConfigured() {
        ShippingPolicy p = policy(3000, 30_000);
        // 설정은 30,000 인데 12,000 을 넘겼다 — VIP 의 인하된 기준이 이렇게 들어온다.
        assertThat(p.feeFor(12_000, 12_000)).isZero();          // 경계: '이상'이므로 무료
        assertThat(p.feeFor(11_999, 12_000)).isEqualTo(3000);
        assertThat(p.amountUntilFree(11_999, 12_000)).isEqualTo(1);
        assertThat(p.amountUntilFree(12_000, 12_000)).isZero();
    }

    /*
     * 🔴 **여기 있던 `noArgFormDelegatesToConfigured` 는 2026-09-01 에 지웠다.**
     * 그 테스트가 지키던 것은 «인자 없는 형태와 받는 형태가 안 갈린다» 였는데,
     * **형태가 하나가 되면 갈릴 것이 없다** — 지킬 대상이 사라져서 함께 지운다.
     * ⚠ **테스트를 «줄이려고» 지운 것이 아니다.** 남겨 두면 없는 메서드를 부르니 컴파일도 안 된다.
     * 🔴 이런 테스트는 «규칙이 두 벌일 때만» 값이 있다 — 벌을 하나로 만드는 것이 더 나은 해법이고,
     * 그게 오늘 J-1~J-4 에서 한 일과 같은 방향이다(2026-09-01 §8).
     */

    @Test
    @DisplayName("넘긴 기준이 0 이하면 무료배송 없음 — 설정이 30,000 이어도 무료가 되지 않는다")
    void givenZeroThresholdMeansNoFreeShipping() {
        // 🔴 설정값으로 슬쩍 되돌아가면 «무료배송 없음» 을 넘겼는데 무료가 나온다.
        ShippingPolicy p = policy(3000, 30_000);
        assertThat(p.feeFor(1_000_000, 0)).isEqualTo(3000);
        assertThat(p.feeFor(1_000_000, -1)).isEqualTo(3000);
        assertThat(p.amountUntilFree(1_000, 0)).isZero();
    }

    @Test
    @DisplayName("🔴 feeFor 와 amountUntilFree 는 같은 기준에서 서로 어긋나지 않는다")
    void feeAndRemainingAgreeOnSameThreshold() {
        // 한쪽만 등급 기준을 쓰면 "N원 더 담으면 무료배송"이라 말해 놓고 이미 무료인 상태가 된다.
        ShippingPolicy p = policy(3000, 30_000);
        for (long threshold : new long[] {12_000, 18_000, 24_000, 30_000}) {
            for (long total = 0; total <= 32_000; total += 1_000) {
                boolean free = p.feeFor(total, threshold) == 0 && total > 0;
                boolean saysAlreadyFree = p.amountUntilFree(total, threshold) == 0;
                assertThat(saysAlreadyFree)
                        .as("total=%d threshold=%d", total, threshold)
                        .isEqualTo(free || total == 0);
            }
        }
    }
}
