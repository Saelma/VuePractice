package com.glassvue.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 기간 경계는 <b>KST 한 곳</b>이 만든다(B-26).
 *
 * <p>🔴 <b>여기서 지키는 것은 «값» 이 아니라 «JVM 시간대에 안 흔들린다» 는 성질이다.</b>
 * 2026-09-07 까지 {@code Transform} 이 {@code ZoneId.systemDefault()} 를 썼고, 서버가
 * {@code Asia/Seoul} 이라 <b>결과가 우연히 맞았다.</b> 그 상태에서도 «시스템 기본으로 계산한 값»
 * 과 비교했다면 초록이었을 것이므로, ⚠ <b>기대값을 UTC 로 못 박아 적는다.</b>
 */
class KstDatesTest {

    @Test
    @DisplayName("하루의 시작은 KST 00:00 = 전날 15:00 UTC")
    void startOfDay_isKstMidnight() {
        assertThat(KstDates.startOfDay(LocalDate.of(2026, 9, 7)))
                .isEqualTo(Instant.parse("2026-09-06T15:00:00Z"));
    }

    @Test
    @DisplayName("종료일은 «포함» 이다 — 다음 날 00:00(KST) 미만으로 옮긴다")
    void startOfNextDay_makesEndInclusive() {
        Instant upper = KstDates.startOfNextDay(LocalDate.of(2026, 9, 7));
        assertThat(upper).isEqualTo(Instant.parse("2026-09-07T15:00:00Z"));

        // 🔴 23:59:59 로 잘랐다면 빠졌을 시각(KST 23:59:59.5) — 초 미만은 눈에 안 보여 더 나쁘다.
        assertThat(Instant.parse("2026-09-07T14:59:59.500Z")).isBefore(upper);
        // 대조군: 다음 날 첫 순간은 안 들어온다.
        assertThat(Instant.parse("2026-09-07T15:00:00Z")).isAfterOrEqualTo(upper);
    }

    @Test
    @DisplayName("경계는 시스템 기본 시간대와 무관하게 고정이다")
    void independentOfSystemZone() {
        LocalDate day = LocalDate.of(2026, 9, 7);
        // ⚠ 실제로 기본 시간대를 흔들지 않는다 — 흔들면 다른 테스트로 샌다.
        //    대신 «시스템 기본으로 계산한 값» 과 갈리는지를 명시하고, KstDates 값은 못 박는다.
        Instant bySystem = day.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant byKst = KstDates.startOfDay(day);
        if (ZoneId.systemDefault().getRules().equals(KstDates.KST.getRules())) {
            assertThat(byKst).isEqualTo(bySystem);
        } else {
            assertThat(byKst).isNotEqualTo(bySystem);
        }
        assertThat(byKst).isEqualTo(Instant.parse("2026-09-06T15:00:00Z"));
    }

    @Test
    @DisplayName("today() 는 KST 달력의 오늘이다")
    void today_isKstCalendarDay() {
        assertThat(KstDates.today()).isEqualTo(LocalDate.now(ZoneId.of("Asia/Seoul")));
    }
}
