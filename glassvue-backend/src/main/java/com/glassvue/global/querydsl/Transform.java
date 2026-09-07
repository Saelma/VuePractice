package com.glassvue.global.querydsl;

import com.glassvue.global.common.KstDates;
import java.time.LocalDate;

/**
 * 조건 값 변환 — {@code @Cond} 가 붙은 검색 값을 비교 직전에 바꾼다.
 * 대표적으로 {@code LocalDate} 범위 검색을 엔티티의 {@code Instant} 컬럼에 맞춘다.
 *
 * <p>🔴 <b>{@code DATE_*} 는 2026-09-07 까지 {@code ZoneId.systemDefault()} 를 썼다.</b>
 * 서버 시간대가 {@code Asia/Seoul} 이라 결과는 맞았지만 <b>«맞기로 정한» 것이 아니라 «우연히 맞은»</b>
 * 것이었다 — 경계는 {@link KstDates} 한 곳이 만든다.
 */
public enum Transform {
    NONE {
        @Override
        Object apply(Object v) {
            return v;
        }
    },
    /** LocalDate → 그 날 00:00 Instant (시작일 이상 비교용) */
    DATE_START {
        @Override
        Object apply(Object v) {
            return KstDates.startOfDay((LocalDate) v);
        }
    },
    /** LocalDate → 다음 날 00:00 Instant (종료일 당일 포함, "< 다음날" 비교용) */
    DATE_NEXT {
        @Override
        Object apply(Object v) {
            return KstDates.startOfNextDay((LocalDate) v);
        }
    };

    abstract Object apply(Object v);
}
