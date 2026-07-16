package com.glassvue.domain.inquiry.repository;

import com.glassvue.domain.inquiry.entity.Inquiry;
import com.glassvue.domain.inquiry.entity.QInquiry;
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
 * 상품별 문의 목록. 조건은 productId 하나라 순수 QueryDSL.
 * 정렬: 요청 sort가 있으면 화이트리스트 검증, 없으면 최신순.
 */
@RequiredArgsConstructor
public class InquiryRepositoryImpl implements InquiryRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private static final QInquiry inquiry = QInquiry.inquiry;

    private static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt", "status");

    @Override
    public Page<Inquiry> findByProduct(UUID productId, Pageable pageable) {
        OrderSpecifier<?>[] orders = pageable.getSort().isSorted()
                ? SortSupport.toOrders(pageable.getSort(), inquiry, SORTABLE)
                : new OrderSpecifier<?>[]{inquiry.createdAt.desc()};

        JPAQuery<Inquiry> content = queryFactory
                .selectFrom(inquiry)
                .where(inquiry.productId.eq(productId))
                .orderBy(orders);

        JPAQuery<Long> count = queryFactory
                .select(inquiry.count())
                .from(inquiry)
                .where(inquiry.productId.eq(productId));

        return QueryDslSupport.page(content, count, pageable);
    }
}
