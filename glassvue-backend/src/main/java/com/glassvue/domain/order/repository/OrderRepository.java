package com.glassvue.domain.order.repository;

import com.glassvue.domain.order.entity.Order;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByMemberIdOrderByCreatedAtDesc(UUID memberId);

    Optional<Order> findByIdAndMemberId(UUID id, UUID memberId);

    /** 회원이 해당 상품을 실제로 주문(취소되지 않은 ORDERED)했는지 — 리뷰 구매 인증용. */
    @Query("""
            select count(oi) > 0 from OrderItem oi
            where oi.order.memberId = :memberId
              and oi.productId = :productId
              and oi.order.status = com.glassvue.domain.order.entity.OrderStatus.ORDERED
            """)
    boolean existsPurchase(@Param("memberId") UUID memberId, @Param("productId") UUID productId);
}
