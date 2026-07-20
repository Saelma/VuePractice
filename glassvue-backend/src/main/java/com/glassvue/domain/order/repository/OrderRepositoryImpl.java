package com.glassvue.domain.order.repository;

import com.glassvue.domain.order.dto.OrderSearchCondition;
import com.glassvue.domain.order.entity.Order;
import com.glassvue.domain.order.entity.QOrder;
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
 * 주문 동적 검색 — 상태·구매자 닉네임·소유자 스코프 전부 {@code @Cond}로 떨어진다(직접 처리 없음).
 *
 * <p><b>items를 fetch join 하지 않는 이유</b>: 컬렉션 fetch join + 페이징을 같이 쓰면 Hibernate가
 * 전체를 메모리로 올려 페이징한다(HHH000104). 대신 {@code Order.items}에 {@code @BatchSize}를 걸어
 * 페이지 전체의 items를 IN 쿼리 한 번으로 가져온다 — 20건 조회 시 총 2~3쿼리로 끝난다.
 */
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private static final QOrder order = QOrder.order;
    private static final Set<String> SORTABLE = Set.of("createdAt", "totalPrice", "status", "buyerNickname");

    @Override
    public Page<Order> search(OrderSearchCondition c, Pageable pageable) {
        BooleanBuilder where = ConditionBuilder.of(order, c).build(); // status, buyerNickname, memberId

        OrderSpecifier<?>[] orders = pageable.getSort().isSorted()
                ? SortSupport.toOrders(pageable.getSort(), order, SORTABLE)
                : new OrderSpecifier<?>[]{order.createdAt.desc()};

        JPAQuery<Order> content = queryFactory.selectFrom(order).where(where).orderBy(orders);
        JPAQuery<Long> count = queryFactory.select(order.count()).from(order).where(where);

        return QueryDslSupport.page(content, count, pageable);
    }
}
