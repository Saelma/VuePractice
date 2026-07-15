package com.glassvue.domain.notice.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import com.glassvue.domain.notice.dto.NoticeSearchCondition;
import com.glassvue.domain.notice.entity.Notice;
import com.glassvue.domain.notice.entity.QNotice;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

/**
 * QueryDSL 동적 검색. 제목·작성자·작성일 범위를 조건이 있는 것만 적용한다.
 * 정렬: 상단고정 우선 → 최신순.
 */
@RequiredArgsConstructor
public class NoticeRepositoryImpl implements NoticeRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private static final QNotice notice = QNotice.notice;

    @Override
    public Page<Notice> search(NoticeSearchCondition c, Pageable pageable) {
        List<Notice> content = queryFactory
                .selectFrom(notice)
                .where(
                        titleContains(c.title()),
                        authorContains(c.author()),
                        createdOnOrAfter(c.fromDate()),
                        createdBefore(c.toDate())
                )
                .orderBy(notice.pinned.desc(), notice.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(notice.count())
                .from(notice)
                .where(
                        titleContains(c.title()),
                        authorContains(c.author()),
                        createdOnOrAfter(c.fromDate()),
                        createdBefore(c.toDate())
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanExpression titleContains(String title) {
        return StringUtils.hasText(title) ? notice.title.containsIgnoreCase(title) : null;
    }

    private BooleanExpression authorContains(String author) {
        return StringUtils.hasText(author) ? notice.author.containsIgnoreCase(author) : null;
    }

    private BooleanExpression createdOnOrAfter(LocalDate from) {
        if (from == null) {
            return null;
        }
        return notice.createdAt.goe(from.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private BooleanExpression createdBefore(LocalDate to) {
        if (to == null) {
            return null;
        }
        // 종료일 당일 포함을 위해 다음 날 0시 미만으로 비교
        return notice.createdAt.lt(to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
