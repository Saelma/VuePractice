package com.glassvue.global.querydsl;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.EntityPathBase;
import com.querydsl.core.types.dsl.PathBuilder;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Sort;

/**
 * Spring Data Sort → QueryDSL OrderSpecifier[] 변환.
 * (없는 필드명을 주면 쿼리 실행 시 오류가 나므로, 필요하면 호출부에서 화이트리스트 검증)
 */
public final class SortSupport {

    private SortSupport() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static OrderSpecifier<?>[] toOrders(Sort sort, EntityPathBase<?> qRoot) {
        PathBuilder<?> root = new PathBuilder<>(qRoot.getType(), qRoot.getMetadata());
        List<OrderSpecifier<?>> orders = new ArrayList<>();
        for (Sort.Order o : sort) {
            var path = root.getComparable(o.getProperty(), Comparable.class);
            orders.add(new OrderSpecifier(o.isAscending() ? Order.ASC : Order.DESC, path));
        }
        return orders.toArray(new OrderSpecifier<?>[0]);
    }
}
