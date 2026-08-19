package com.glassvue.domain.catalog.repository;

import com.glassvue.domain.catalog.entity.ProductDiscount;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductDiscountRepository extends JpaRepository<ProductDiscount, UUID> {

    /** 한 상품의 할인 일정 전부(관리자 화면 — 지난 것·진행 중·예정을 시간순으로 함께 본다). */
    List<ProductDiscount> findByProductIdOrderByStartsAtAsc(UUID productId);

    /**
     * 지금 유효한 할인 — 여러 상품을 한 번에(N+1 회피).
     *
     * <p>🔴 <b>상품당 하나가 아니라 여러 건이 나올 수 있다.</b> 기간 겹침은 DB 가 못 막고 앱이 유일한
     * 방어라(V52 주석), 그 방어가 뚫리면 한 상품에 유효한 할인이 둘 이상 있게 된다.
     * 여기서 {@code Optional} 로 받으면 그 순간 <b>목록·홈 전체가 500</b> 이 된다 —
     * G-8 에서 «열린 이벤트 둘» 로 겪은 그 자리다. 그래서 <b>List 로 받아 호출자가 고른다.</b>
     *
     * <p>정렬이 «할인율 높은 순» 인 이유도 그것이다: 뚫렸을 때 <b>고객에게 유리한 쪽</b>이 사고가 덜 난다
     * (더 비싸게 청구하는 것보다 낫다). 같은 할인율이면 먼저 시작한 것을 앞에 둬 순서를 안정화한다.
     */
    @Query("""
            select d from ProductDiscount d
            where d.productId in :productIds
              and d.startsAt <= :at
              and d.endsAt > :at
            order by d.rate desc, d.startsAt asc
            """)
    List<ProductDiscount> findActive(@Param("productIds") Collection<UUID> productIds,
                                     @Param("at") Instant at);

    /**
     * 기간이 겹치는 할인이 이미 있나 — <b>등록·수정 가드가 쓰는 유일한 질문</b>이다.
     *
     * <p>⚠ 겹침 판정은 {@code 기존.시작 < 새.끝 AND 기존.끝 > 새.시작} 이다. 경계가 <b>맞닿는 것은
     * 겹침이 아니다</b> — 종료가 배타라 «8/20 끝» 과 «8/20 시작» 은 한 순간도 함께 유효하지 않다.
     * 이걸 {@code <=} 로 쓰면 <b>연속된 세일을 이어 붙일 수 없다.</b>
     *
     * <p>⚠ {@code excludeId} 는 <b>수정</b>을 위한 것이다 — 자기 자신과 겹친다고 스스로를 거절하면
     * 기간을 그대로 두고 할인율만 고치는 것이 불가능해진다. 신규 등록은 존재하지 않는 UUID 를 넘긴다.
     */
    @Query("""
            select d from ProductDiscount d
            where d.productId = :productId
              and d.id <> :excludeId
              and d.startsAt < :endsAt
              and d.endsAt > :startsAt
            order by d.startsAt asc
            """)
    List<ProductDiscount> findOverlapping(@Param("productId") UUID productId,
                                          @Param("startsAt") Instant startsAt,
                                          @Param("endsAt") Instant endsAt,
                                          @Param("excludeId") UUID excludeId);
}
