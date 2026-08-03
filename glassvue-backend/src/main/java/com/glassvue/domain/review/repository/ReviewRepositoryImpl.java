package com.glassvue.domain.review.repository;

import com.glassvue.domain.review.entity.QReview;
import com.glassvue.domain.review.entity.Review;
import com.glassvue.global.querydsl.QueryDslSupport;
import com.glassvue.global.querydsl.SortSupport;
import com.querydsl.core.BooleanBuilder;
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
    // ⚠ 이 목록은 **B-22 이전부터 있었다** — 백엔드는 별점순 정렬을 이미 지원했고,
    //    화면이 ?sort 를 안 보내서 최신순만 쓰이고 있었을 뿐이다(2026-08-03 실측).
    private static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt", "rating");

    @Override
    public Page<Review> findByProduct(UUID productId, boolean photoOnly, Pageable pageable) {
        OrderSpecifier<?>[] orders = pageable.getSort().isSorted()
                ? SortSupport.toOrders(pageable.getSort(), review, SORTABLE)
                : new OrderSpecifier<?>[]{review.createdAt.desc()};

        // ⚠ 조건을 **한 곳에서** 만들어 목록·카운트가 같은 것을 쓰게 한다.
        //    둘이 갈리면 "3건" 이라 써 놓고 목록엔 5줄이 뜬다(2026-08-03 B-16 에서 겪은 자리, WA §3).
        BooleanBuilder where = new BooleanBuilder(review.productId.eq(productId));
        if (photoOnly) {
            // 포토 리뷰는 image_group_id 로만 구분된다(Review 엔티티 주석) — 그룹이 있으면 사진이 있다.
            where.and(review.imageGroupId.isNotNull());
        }

        JPAQuery<Review> content = queryFactory
                .selectFrom(review)
                .where(where)
                .orderBy(orders);

        JPAQuery<Long> count = queryFactory
                .select(review.count())
                .from(review)
                .where(where);

        return QueryDslSupport.page(content, count, pageable);
    }
}
