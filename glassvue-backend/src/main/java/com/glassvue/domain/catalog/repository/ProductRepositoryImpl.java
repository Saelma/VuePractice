package com.glassvue.domain.catalog.repository;

import com.glassvue.domain.catalog.dto.ProductSearchCondition;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.entity.QProduct;
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
 * 상품 동적 검색. 이름·가격·상태는 @Cond(ConditionBuilder)로 자동,
 * 카테고리(연관)는 ConditionBuilder로 안 떨어져 직접 QueryDSL로 처리한다(탈출구 예시).
 */
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private static final QProduct product = QProduct.product;
    // 정렬 허용 필드(화이트리스트). avgRating·soldCount·reviewCount는 이벤트로 비정규화해 둔 컬럼이라
    // (V4·V25) 조인 없이 정렬할 수 있다 — "평점순"·홈의 "인기순"(판매량)·"리뷰 많은순"을 위해 열어둔다.
    // ⚠ reviewCount 는 avgRating 과 한 몸이다(같은 ReviewRatingChangedEvent 가 둘을 함께 갱신).
    // 평점순만 열려 있으면 "별 5개 리뷰 1건"이 최상단에 오는데, 리뷰 수 정렬은 그 반대편을 보여준다.
    private static final Set<String> SORTABLE =
            Set.of("createdAt", "price", "stock", "name", "avgRating", "soldCount", "reviewCount");

    @Override
    public Page<Product> search(ProductSearchCondition c, Pageable pageable) {
        BooleanBuilder where = ConditionBuilder.of(product, c).build(); // name, price(min/max), status
        if (c.categoryId() != null) {
            where.and(product.category.id.eq(c.categoryId())); // 연관 필터는 직접
        }

        OrderSpecifier<?>[] orders = pageable.getSort().isSorted()
                ? SortSupport.toOrders(pageable.getSort(), product, SORTABLE)
                : new OrderSpecifier<?>[]{product.createdAt.desc()};

        JPAQuery<Product> content = queryFactory.selectFrom(product).where(where).orderBy(orders);
        JPAQuery<Long> count = queryFactory.select(product.count()).from(product).where(where);

        return QueryDslSupport.page(content, count, pageable);
    }
}
