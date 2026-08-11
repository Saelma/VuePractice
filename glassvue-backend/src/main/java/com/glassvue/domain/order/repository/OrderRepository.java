package com.glassvue.domain.order.repository;

import com.glassvue.domain.order.entity.Order;
import com.glassvue.domain.order.entity.OrderStatus;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID>, OrderRepositoryCustom {

    Optional<Order> findByIdAndMemberId(UUID id, UUID memberId);

    /**
     * 주문번호용 일련번호를 뽑는다(V15의 {@code seq_order_no}).
     *
     * <p>PK가 아니라 <b>표시·검색용</b> 번호다 — PK는 UUIDv7 그대로다(CLAUDE.md의 SEQUENCE 금지는 PK 규칙).
     * 시퀀스를 쓰는 이유는 동시 주문에서 <b>같은 번호를 잡는 일이 원천적으로 불가능</b>하기 때문이다.
     * 일자별로 1부터 리셋하려면 카운터 락이나 유니크 충돌 재시도가 필요해진다.
     */
    @Query(value = "SELECT seq_order_no.NEXTVAL FROM dual", nativeQuery = true)
    long nextOrderNoSequence();

    /**
     * 회원이 해당 상품을 실제로 주문(취소되지 않은 주문)했는지 — 리뷰 구매 인증용.
     *
     * <p>「구매함」으로 치는 상태를 <b>{@link OrderStatus#purchaseProven()} 한 곳</b>에서 받는다.
     * 상태를 여기 문자로 열거하던 것을 2026-08-11 에 옮겼다 — 이유는 아래.
     *
     * <p>⚠ <b>같은 자리가 두 번 어긋났다.</b> 2026-07-20 에 {@code ORDERED} 만 세던 것을
     * {@code ORDERED·PAID·SHIPPED} 로 고쳤는데(그때는 그게 «CANCELLED만 제외» 였다), 2026-07-23(V13)에
     * {@code DELIVERED} 가 생기면서 <b>다시 빠졌다.</b> «새 상태는 여기 직접 추가한다(opt-in)» 는
     * 주석은 의도를 적어 뒀지만, <b>추가할 계기를 아무도 못 받는다</b> — 열거를 손으로 늘리는 모양
     * 자체가 원인이라, opt-in 을 <b>enum 옆으로 옮겨</b> 상태를 새로 만드는 사람의 눈앞에 뒀다.
     *
     * <p>⚠ <b>리뷰를 쓰는 가장 자연스러운 시점이 배송완료다.</b> 그런데 주문 직후~발송 사이엔 통과하므로
     * 손으로 눌러 보는 검증에서는 안 드러난다 — 「받고 나서」 눌러야만 막혔다.
     */
    @Query("""
            select count(oi) > 0 from OrderItem oi
            where oi.order.memberId = :memberId
              and oi.productId = :productId
              and oi.order.status in :provenStatuses
            """)
    boolean existsPurchaseIn(@Param("memberId") UUID memberId,
                             @Param("productId") UUID productId,
                             @Param("provenStatuses") Set<OrderStatus> provenStatuses);

    /** {@link #existsPurchaseIn} 의 호출부용 — 「구매함」의 기준은 호출부가 고르지 않는다. */
    default boolean existsPurchase(UUID memberId, UUID productId) {
        return existsPurchaseIn(memberId, productId, OrderStatus.purchaseProven());
    }

    /**
     * 상태별 주문 건수 — 관리자 화면이 "지금 발송할 게 몇 건인지"를 한눈에 보여주기 위함.
     *
     * <p>상태 수만큼 count 쿼리를 날리지 않고 **group by 한 번**으로 끝낸다.
     * 조건이 고정이라 QueryDSL 대신 JPQL(ARCHITECTURE 쿼리 작성 기준).
     * 건수가 0인 상태는 행 자체가 없으므로 호출부가 0으로 채운다.
     */
    @Query("select o.status, count(o) from Order o group by o.status")
    List<Object[]> countByStatus();
}
