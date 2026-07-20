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
}
