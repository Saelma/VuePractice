package com.glassvue.domain.inquiry.repository;

import com.glassvue.domain.inquiry.entity.Inquiry;
import com.glassvue.domain.inquiry.entity.InquiryStatus;
import com.glassvue.domain.inquiry.entity.QInquiry;
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

    /**
     * 관리자 목록 — 상품 조건이 없다(가로지른다). status 가 null 이면 전체.
     *
     * <p>⚠ <b>위 {@code findByProduct} 는 이 변화에 손댈 필요가 없다.</b> G-3 2단계에서
     * {@code product_id} 가 nullable 이 되어도 {@code productId.eq(...)} 는 NULL 행을 못 잡는다
     * (SQL 에서 {@code = NULL} 은 참이 될 수 없다) — 즉 «일반 문의» 는 상품 문의 목록에
     * <b>구조적으로 섞이지 않는다.</b> 「전체 문의」를 새로 조회하는 여기서만 신경 쓰면 된다.
     */
    @Override
    public Page<Inquiry> findForAdmin(InquiryStatus status, Pageable pageable) {
        OrderSpecifier<?>[] orders = pageable.getSort().isSorted()
                ? SortSupport.toOrders(pageable.getSort(), inquiry, SORTABLE)
                : new OrderSpecifier<?>[]{inquiry.createdAt.desc()};

        // ⚠ 목록·카운트가 **같은 조건 객체**를 쓴다 — 둘이 갈리면 "3건" 이라 써 놓고 목록엔 5줄이 뜬다.
        BooleanBuilder where = new BooleanBuilder();
        if (status != null) {
            where.and(inquiry.status.eq(status));
        }

        JPAQuery<Inquiry> content = queryFactory.selectFrom(inquiry).where(where).orderBy(orders);
        JPAQuery<Long> count = queryFactory.select(inquiry.count()).from(inquiry).where(where);

        return QueryDslSupport.page(content, count, pageable);
    }

    /**
     * 내 문의 목록 (2026-08-07, G-3 3단계) — 상품 문의·일반 문의를 <b>가르지 않는다</b>.
     *
     * <p>조건이 authorId 하나라 {@code findByProduct} 와 같은 모양이다. 유형·상태 필터를 두지 않은 이유:
     * 한 사람이 쓰는 문의는 많아야 수십 건이라 <b>거를 것이 없다</b> — 관리자 목록(수백 건을 가로지른다)과
     * 성격이 다르다. 필요해지면 그때 붙인다.
     */
    @Override
    public Page<Inquiry> findByAuthor(UUID authorId, Pageable pageable) {
        OrderSpecifier<?>[] orders = pageable.getSort().isSorted()
                ? SortSupport.toOrders(pageable.getSort(), inquiry, SORTABLE)
                : new OrderSpecifier<?>[]{inquiry.createdAt.desc()};

        JPAQuery<Inquiry> content = queryFactory
                .selectFrom(inquiry)
                .where(inquiry.authorId.eq(authorId))
                .orderBy(orders);

        JPAQuery<Long> count = queryFactory
                .select(inquiry.count())
                .from(inquiry)
                .where(inquiry.authorId.eq(authorId));

        return QueryDslSupport.page(content, count, pageable);
    }
}
