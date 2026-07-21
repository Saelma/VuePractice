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
