package com.glassvue.domain.catalog.repository;

import com.glassvue.domain.catalog.dto.ProductSearchCondition;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.entity.QProduct;
import com.glassvue.domain.catalog.entity.QProductDiscount;
import com.glassvue.global.querydsl.ConditionBuilder;
import com.glassvue.global.querydsl.QueryDslSupport;
import com.glassvue.global.querydsl.SortSupport;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 상품 동적 검색. 이름·상태는 @Cond(ConditionBuilder)로 자동,
 * 카테고리(연관)와 <b>가격</b>은 ConditionBuilder로 안 떨어져 직접 QueryDSL로 처리한다(탈출구 예시).
 *
 * <p>🔴 <b>가격이 탈출구로 내려온 것이 2026-08-19(G-5)의 변경이다.</b> 그전엔 {@code minPrice}·
 * {@code maxPrice} 가 {@code @Cond(path="price")} 로 자동 처리돼 <b>{@code product.price} 컬럼</b>을
 * 그대로 봤다. 기간 할인이 생기면서 그 값이 «지금 파는 가격» 이 아니게 됐다 —
 * 화면엔 8,000원(세일)인데 「1만원 이하」 필터에 안 걸리고 「가격 낮은 순」이 세일 전 순서로 나온다.
 * <b>화면에만 있고 조회에는 없는 규칙</b>이 되는 자리다(2026-08-13 §13-2 와 같은 종류).
 */
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private static final QProduct product = QProduct.product;
    private static final QProductDiscount discount = QProductDiscount.productDiscount;

    // 정렬 허용 필드(화이트리스트). avgRating·soldCount·reviewCount는 이벤트로 비정규화해 둔 컬럼이라
    // (V4·V25) 조인 없이 정렬할 수 있다 — "평점순"·홈의 "인기순"(판매량)·"리뷰 많은순"을 위해 열어둔다.
    // ⚠ reviewCount 는 avgRating 과 한 몸이다(같은 ReviewRatingChangedEvent 가 둘을 함께 갱신).
    // 평점순만 열려 있으면 "별 5개 리뷰 1건"이 최상단에 오는데, 리뷰 수 정렬은 그 반대편을 보여준다.
    // ⚠ "price" 는 여기 남아 있지만 SortSupport 로 가지 않는다 — 아래 orders() 가 가로챈다.
    private static final Set<String> SORTABLE =
            Set.of("createdAt", "price", "stock", "name", "avgRating", "soldCount", "reviewCount");

    /** 정렬·필터가 가로채는 필드 이름. 화이트리스트 검사는 그대로 통과시키고 식만 바꾼다. */
    private static final String PRICE = "price";

    @Override
    public Page<Product> search(ProductSearchCondition c, Pageable pageable) {
        Instant now = Instant.now();
        NumberExpression<Long> effectivePrice = effectivePrice(now);

        BooleanBuilder where = ConditionBuilder.of(product, c).build(); // name, status
        // 🔴 삭제 대기 상품은 목록·검색에서 뺀다(2026-08-12, F-7). **이 한 줄이 빠지면 지운 상품이
        //    계속 팔린다** — 그리고 화면은 멀쩡해 보인다. content 와 count 가 같은 where 를 쓰므로
        //    여기 한 곳이면 둘 다 걸린다(2026-08-11 에 count 쪽만 갈린 사고가 있었다).
        where.and(product.deletedAt.isNull());
        if (c.categoryId() != null) {
            where.and(product.category.id.eq(c.categoryId())); // 연관 필터는 직접
        }
        // 🔴 가격 범위는 **세일가 기준**이다(2026-08-19, G-5, 사용자 결정).
        //    ⚠ 세일 중이 아닌 상품은 할인율 0이라 effectivePrice = price 다 — 즉 이 변경은
        //    세일이 없을 때 **예전과 완전히 같은 결과**를 낸다. 그게 이 식이 안전한 이유다.
        if (c.minPrice() != null) {
            where.and(effectivePrice.goe(c.minPrice()));
        }
        if (c.maxPrice() != null) {
            where.and(effectivePrice.loe(c.maxPrice()));
        }

        JPAQuery<Product> content = queryFactory.selectFrom(product).where(where)
                .orderBy(orders(pageable.getSort(), effectivePrice));
        JPAQuery<Long> count = queryFactory.select(product.count()).from(product).where(where);

        return QueryDslSupport.page(content, count, pageable);
    }

    /**
     * 지금 이 순간의 <b>유효 판매가</b> — {@code ROUND(price × (100 - 할인율) / 100)}.
     *
     * <p>⚠ <b>반올림 식이 {@code ProductDiscount.applyTo} 와 같아야 한다.</b> 갈리면 목록에 뜬 가격과
     * 장바구니 가격이 1원 어긋나는데, 그건 눈에 잘 안 띄고 <b>결제에서 터진다.</b>
     * Java 쪽은 정수 연산({@code (x*(100-r)+50)/100})이고 Oracle {@code ROUND(x, 0)} 이 그것과 같다
     * (가격이 음수가 아니므로).
     *
     * <p>🔴 <b>조인이 아니라 상관 서브쿼리다.</b> 조인하면 «유효한 할인이 둘» 인 상품이 목록에
     * <b>두 줄로 나온다</b> — 기간 겹침은 DB 가 못 막고 앱이 유일한 방어라(V52) 그 상태가 실제로 가능하다.
     * G-8 에서 «열린 이벤트 둘» 이 홈을 500 으로 만들 뻔한 것과 같은 자리인데, 여기선 500 이 아니라
     * <b>조용한 중복 행</b>이라 더 나쁘다(아무 로그도 안 남는다). {@code max(rate)} 로 한 값을 뽑으면
     * 그 갈래가 아예 생기지 않고, 고르는 기준도 응답 쪽과 같다(할인율이 가장 높은 것).
     *
     * <p>⚠ 옵션 가격차({@code price_delta})는 여기 안 들어간다 — 목록이 보여주는 값은
     * <b>상품 기본가</b>다(옵션마다 값이 다르면 목록에 한 값을 못 쓴다). 옵션별 세일가는 응답 조립에서
     * 만든다({@code VariantResponse}).
     */
    private NumberExpression<Long> effectivePrice(Instant now) {
        return Expressions.numberTemplate(Long.class,
                "round({0} * (100 - coalesce({1}, 0)) / 100, 0)",
                product.price,
                JPAExpressions.select(discount.rate.max())
                        .from(discount)
                        .where(discount.productId.eq(product.id),
                                discount.startsAt.loe(now),
                                discount.endsAt.gt(now)));
    }

    /**
     * 정렬 — {@code price} 만 가로채 유효 판매가로 바꾸고 나머지는 그대로 위임한다.
     *
     * <p>⚠ 화이트리스트 검사(400)는 {@link SortSupport} 가 하던 대로 살아 있어야 한다. 그래서
     * 가로챈 필드도 {@code SORTABLE} 에 남겨 두고, <b>모르는 필드가 오면 여기서 걸리지 않고</b>
     * SortSupport 까지 내려가 400 이 나게 둔다.
     */
    private OrderSpecifier<?>[] orders(Sort sort, NumberExpression<Long> effectivePrice) {
        if (sort.isUnsorted()) {
            return new OrderSpecifier<?>[]{product.createdAt.desc()};
        }
        // ⚠ **한 건씩 순서대로** 만든다. price 만 모아서 앞에 붙이면 `?sort=name,price` 의
        //    1순위·2순위가 뒤바뀌는데, 화면은 «정렬이 좀 이상하다» 로만 보이고 무엇이 틀렸는지 안 나온다.
        List<OrderSpecifier<?>> result = new ArrayList<>();
        for (Sort.Order o : sort) {
            if (PRICE.equals(o.getProperty())) {
                result.add(new OrderSpecifier<>(o.isAscending() ? Order.ASC : Order.DESC, effectivePrice));
            } else {
                result.addAll(List.of(SortSupport.toOrders(Sort.by(o), product, SORTABLE)));
            }
        }
        return result.toArray(new OrderSpecifier<?>[0]);
    }
}
