package com.glassvue.domain.notice.repository;

import com.glassvue.domain.notice.dto.NoticeSearchCondition;
import com.glassvue.domain.notice.entity.Notice;
import com.glassvue.domain.notice.entity.QNotice;
import com.glassvue.global.querydsl.ConditionBuilder;
import com.glassvue.global.querydsl.QueryDslSupport;
import com.glassvue.global.querydsl.SortSupport;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 공지 동적 검색. 조건은 @Cond 기반 ConditionBuilder가 자동 생성한다.
 * 정렬: 요청 sort가 있으면 그대로, 없으면 상단고정 우선 → 최신순.
 */
@RequiredArgsConstructor
public class NoticeRepositoryImpl implements NoticeRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private static final QNotice notice = QNotice.notice;

    // 정렬 허용 필드(화이트리스트). 그 외 ?sort=필드 는 400.
    private static final Set<String> SORTABLE =
            Set.of("createdAt", "updatedAt", "viewCount", "title", "author", "pinned");

    @Override
    public Page<Notice> search(NoticeSearchCondition condition, Pageable pageable) {
        BooleanBuilder where = ConditionBuilder.of(notice, condition).build();

        OrderSpecifier<?>[] orders = pageable.getSort().isSorted()
                ? SortSupport.toOrders(pageable.getSort(), notice, SORTABLE)
                : new OrderSpecifier<?>[]{notice.pinned.desc(), notice.createdAt.desc()};

        JPAQuery<Notice> content = queryFactory
                .selectFrom(notice)
                .where(where)
                .orderBy(orders);

        JPAQuery<Long> count = queryFactory
                .select(notice.count())
                .from(notice)
                .where(where);

        return QueryDslSupport.page(content, count, pageable);
    }
}
