package com.glassvue.domain.order.repository;

import com.glassvue.domain.order.entity.Order;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByMemberIdOrderByCreatedAtDesc(UUID memberId);

    Optional<Order> findByIdAndMemberId(UUID id, UUID memberId);
}
