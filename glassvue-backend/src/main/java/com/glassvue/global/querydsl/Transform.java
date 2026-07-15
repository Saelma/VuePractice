package com.glassvue.global.querydsl;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 조건 값 변환. 대표적으로 LocalDate 범위 검색을 엔티티의 Instant 컬럼에 맞춰 변환한다.
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
            return ((LocalDate) v).atStartOfDay(ZoneId.systemDefault()).toInstant();
        }
    },
    /** LocalDate → 다음 날 00:00 Instant (종료일 당일 포함, "< 다음날" 비교용) */
    DATE_NEXT {
        @Override
        Object apply(Object v) {
            return ((LocalDate) v).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        }
    };

    abstract Object apply(Object v);
}
