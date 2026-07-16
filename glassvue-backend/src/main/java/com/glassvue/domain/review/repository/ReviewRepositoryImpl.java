package com.glassvue.domain.review.repository;

import com.glassvue.domain.review.entity.QReview;
import com.glassvue.domain.review.entity.Review;
import com.glassvue.global.querydsl.QueryDslSupport;
import com.glassvue.global.querydsl.SortSupport;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 상품별 리뷰 목록. 조건은 productId 하나뿐이라 순수 QueryDSL로 작성.
 * 정렬: 요청 sort가 있으면 화이트리스트 검증, 없으면 최신순.
 */
@RequiredArgsConstructor
public class ReviewRepositoryImpl implements ReviewRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private static final QReview review = QReview.review;

    // 정렬 허용 필드(화이트리스트). 그 외 ?sort=필드 는 400.
    private static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt", "rating");

    @Override
    public Page<Review> findByProduct(UUID productId, Pageable pageable) {
        OrderSpecifier<?>[] orders = pageable.getSort().isSorted()
                ? SortSupport.toOrders(pageable.getSort(), review, SORTABLE)
                : new OrderSpecifier<?>[]{review.createdAt.desc()};

        JPAQuery<Review> content = queryFactory
                .selectFrom(review)
                .where(review.productId.eq(productId))
                .orderBy(orders);

        JPAQuery<Long> count = queryFactory
                .select(review.count())
                .from(review)
                .where(review.productId.eq(productId));

        return QueryDslSupport.page(content, count, pageable);
    }
}
