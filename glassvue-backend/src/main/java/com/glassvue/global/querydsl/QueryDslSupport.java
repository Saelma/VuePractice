package com.glassvue.global.querydsl;

import com.querydsl.jpa.impl.JPAQuery;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

/**
 * QueryDSL 페이징 실행 헬퍼. content 쿼리에 offset/limit를 적용하고 count로 Page를 만든다.
 * count는 마지막 페이지 등에서 생략될 수 있도록 supplier로 넘긴다.
 */
public final class QueryDslSupport {

    private QueryDslSupport() {
    }

    public static <T> Page<T> page(JPAQuery<T> content, JPAQuery<Long> count, Pageable pageable) {
        List<T> list = content
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        return PageableExecutionUtils.getPage(list, pageable, count::fetchOne);
    }
}
