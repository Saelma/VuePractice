package com.glassvue.global.querydsl;

import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.EntityPathBase;
import com.querydsl.core.types.dsl.PathBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;

/**
 * Spring Data Sort → QueryDSL OrderSpecifier[] 변환.
 * allowed(화이트리스트)에 없는 필드는 400으로 막는다 — 잘못된 ?sort=필드 로 인한 런타임 오류(500) 방지.
 */
public final class SortSupport {

    private SortSupport() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static OrderSpecifier<?>[] toOrders(Sort sort, EntityPathBase<?> qRoot, Set<String> allowed) {
        PathBuilder<?> root = new PathBuilder<>(qRoot.getType(), qRoot.getMetadata());
        List<OrderSpecifier<?>> orders = new ArrayList<>();
        for (Sort.Order o : sort) {
            if (!allowed.contains(o.getProperty())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "정렬할 수 없는 필드입니다: " + o.getProperty() + " (허용: " + allowed + ")");
            }
            var path = root.getComparable(o.getProperty(), Comparable.class);
            orders.add(new OrderSpecifier(o.isAscending() ? Order.ASC : Order.DESC, path));
        }
        return orders.toArray(new OrderSpecifier<?>[0]);
    }
}
