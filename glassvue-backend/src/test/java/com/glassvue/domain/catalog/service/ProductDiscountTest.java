package com.glassvue.domain.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassvue.domain.catalog.entity.ProductDiscount;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 기간 할인의 <b>계산과 경계</b> (2026-08-19, G-5).
 *
 * <p>여기서 고정하는 것 둘:
 * <ul>
 *   <li><b>반올림이 Oracle {@code ROUND} 와 같다</b> — 목록 쿼리(정렬·가격필터)는 SQL 에서 같은 식을
 *       쓰므로, 이 둘이 갈리면 <b>목록에 뜬 가격과 장바구니 가격이 1원 어긋난다.</b></li>
 *   <li><b>시작은 포함, 종료는 배타</b> — 종료를 포함으로 잘못 두면 세일이 하루 더 간다.</li>
 * </ul>
 */
class ProductDiscountTest {

    private static final Instant START = Instant.parse("2026-08-22T15:00:00Z"); // 8/23 00:00 KST
    private static final Instant END = Instant.parse("2026-08-24T15:00:00Z");   // 8/25 00:00 KST

    private ProductDiscount discount(int rate) {
        return ProductDiscount.of(UUID.randomUUID(), rate, START, END);
    }

    @Nested
    @DisplayName("할인가 계산")
    class ApplyTo {

        @ParameterizedTest(name = "{0}원에 {1}% → {2}원")
        @CsvSource({
                // 딱 떨어지는 경우
                "10000, 20, 8000",
                "39000, 10, 35100",
                // 🔴 **반올림이 실제로 일어나는 값들** — 이게 없으면 «반올림 규칙» 을 아무것도 증명 못 한다.
                //    12345 × 87 / 100 = 10740.15 → 10740 (내림)
                "12345, 13, 10740",
                //    12345 × 90 / 100 = 11110.5  → 11111 (**정확히 0.5, 올림**)
                "12345, 10, 11111",
                //    123 × 67 / 100 = 82.41 → 82
                "123, 33, 82",
                //    1 × 99 / 100 = 0.99 → 1  (⚠ 1원짜리에 1% 를 걸면 값이 안 변한다 —
                //    그래도 «세일 중» 인 것은 맞고, 그래서 화면은 discountRate 로 판정한다)
                "1, 1, 1",
                // 경계 할인율
                "10000, 1, 9900",
                "10000, 99, 100",
        })
        @DisplayName("Oracle ROUND(x, 0) 과 같은 반올림 — 0.5는 올린다")
        void rounds(long price, int rate, long expected) {
            assertThat(discount(rate).applyTo(price)).isEqualTo(expected);
        }

        @Test
        @DisplayName("0원짜리는 0원 — 나눗셈이 터지지 않는다")
        void zeroPrice() {
            assertThat(discount(50).applyTo(0L)).isZero();
        }
    }

    @Nested
    @DisplayName("기간 경계")
    class Period {

        @Test
        @DisplayName("시작 시각 **정각은 포함**이다 — 그 순간부터 세일이다")
        void startIsInclusive() {
            assertThat(discount(20).isActiveAt(START)).isTrue();
        }

        @Test
        @DisplayName("시작 1밀리초 전은 아직 아니다")
        void justBeforeStart() {
            ProductDiscount d = discount(20);
            assertThat(d.isActiveAt(START.minusMillis(1))).isFalse();
            assertThat(d.isUpcomingAt(START.minusMillis(1))).isTrue();
        }

        @Test
        @DisplayName("🔴 종료 시각 **정각은 배타**다 — 그 순간 원가로 돌아간다")
        void endIsExclusive() {
            assertThat(discount(20).isActiveAt(END)).isFalse();
        }

        @Test
        @DisplayName("종료 1밀리초 전은 아직 세일이다")
        void justBeforeEnd() {
            assertThat(discount(20).isActiveAt(END.minusMillis(1))).isTrue();
        }

        @Test
        @DisplayName("진행 중이면 「예정」이 아니다 — 화면이 두 배지를 동시에 띄우지 않는다")
        void activeIsNotUpcoming() {
            ProductDiscount d = discount(20);
            Instant mid = START.plusSeconds(3600);
            assertThat(d.isActiveAt(mid)).isTrue();
            assertThat(d.isUpcomingAt(mid)).isFalse();
        }
    }
}
