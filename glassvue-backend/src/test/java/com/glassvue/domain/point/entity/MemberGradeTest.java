package com.glassvue.domain.point.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 등급 판정과 적립률 — <b>경계값</b> (H-2, 2026-07-31).
 *
 * <p>통합테스트({@code PointFlowIntegrationTest})는 행복 경로를 지난다 — 넉넉한 금액으로 사서
 * 적립이 붙는지를 본다. 여기서만 드러나는 건 <b>임계값 바로 앞뒤</b>다:
 * {@code 99,999} 와 {@code 100,000} 은 1원 차이인데 적립률이 두 배로 갈린다.
 * {@code >=} 를 {@code >} 로 한 글자 잘못 쓰면 통합테스트는 전부 통과하고 이 테스트만 깨진다.
 *
 * <p>적립 계산이 <b>정수 내림</b>인 것도 여기서 못박는다. 자바 정수 나눗셈이라 지금은 저절로
 * 맞지만, 나중에 누가 {@code Math.round} 나 {@code double} 로 바꾸면 "적립률 1%" 라는 약속이
 * 조용히 어긋난다 — 99원짜리에 1원이 나가는 식으로.
 */
class MemberGradeTest {

    @ParameterizedTest(name = "누적 {0}원 → {1}")
    @DisplayName("등급 임계값 — 경계 바로 앞뒤")
    @CsvSource({
            "0,          BRONZE",
            "99999,      BRONZE",   // SILVER 바로 앞 (1원 차이)
            "100000,     SILVER",   // 임계값 자신 — 포함(>=)
            "499999,     SILVER",
            "500000,     GOLD",
            "999999,     GOLD",
            "1000000,    VIP",
            "99999999,   VIP",      // 최고 등급 위로는 더 없다
    })
    void of_boundaries(long totalPurchase, MemberGrade expected) {
        assertThat(MemberGrade.of(totalPurchase)).isEqualTo(expected);
    }

    @Test
    @DisplayName("누적액이 음수여도 BRONZE — 최저 등급 아래로 떨어지지 않는다")
    void of_negative() {
        // 정상 경로에선 PointAccount 가 0 아래를 막지만, 판정 자체가 방어적이어야
        // 호출부가 하나 늘 때마다 같은 방어를 다시 짜지 않는다.
        assertThat(MemberGrade.of(-1L)).isEqualTo(MemberGrade.BRONZE);
    }

    @Test
    @DisplayName("적립률은 등급마다 1 · 2 · 3 · 5%")
    void earnPercent() {
        assertThat(MemberGrade.BRONZE.earnPercent()).isEqualTo(1);
        assertThat(MemberGrade.SILVER.earnPercent()).isEqualTo(2);
        assertThat(MemberGrade.GOLD.earnPercent()).isEqualTo(3);
        assertThat(MemberGrade.VIP.earnPercent()).isEqualTo(5);
    }

    @ParameterizedTest(name = "{0} 등급 {1}원 → {2}P")
    @DisplayName("적립 계산은 원 단위 **내림** — 반올림하면 약속이 어긋난다")
    @CsvSource({
            "BRONZE, 99,     0",     // 1% 면 0.99원 → 0. 반올림이면 1이 나간다
            "BRONZE, 100,    1",
            "BRONZE, 199,    1",     // 1.99 → 1
            "SILVER, 10000,  200",
            "SILVER, 149,    2",     // 2.98 → 2 (반올림이면 3)
            "GOLD,   33333,  999",   // 999.99 → 999
            "VIP,    1234,   61",    // 61.7 → 61
            "VIP,    19,     0",     // 5% 여도 20원 미만이면 0원
    })
    void earn_floors(MemberGrade grade, long amount, long expected) {
        assertThat(grade.earn(amount)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "결제금액 {0} → 적립 0")
    @DisplayName("0원·음수 결제엔 적립이 없다")
    @CsvSource({"0", "-1", "-100000"})
    void earn_nonPositive(long amount) {
        assertThat(MemberGrade.VIP.earn(amount)).isZero();
    }
}
