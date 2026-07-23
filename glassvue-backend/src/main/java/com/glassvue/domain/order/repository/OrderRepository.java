package com.glassvue.domain.order.repository;

import com.glassvue.domain.order.entity.Order;
import java.util.List;
import java.util.Optional;
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
     * <p>정상 흐름(ORDERED·PAID·SHIPPED)을 **명시적으로 열거**한다. {@code <> CANCELLED}로 쓰면
     * 나중에 추가되는 상태(환불 등)가 자동으로 "구매함"에 포함돼버리므로, 새 상태는 여기 직접
     * 추가하도록 opt-in으로 둔다.
     */
    @Query("""
            select count(oi) > 0 from OrderItem oi
            where oi.order.memberId = :memberId
              and oi.productId = :productId
              and oi.order.status in (
                    com.glassvue.domain.order.entity.OrderStatus.ORDERED,
                    com.glassvue.domain.order.entity.OrderStatus.PAID,
                    com.glassvue.domain.order.entity.OrderStatus.SHIPPED)
            """)
    boolean existsPurchase(@Param("memberId") UUID memberId, @Param("productId") UUID productId);

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
