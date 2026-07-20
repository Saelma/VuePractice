package com.glassvue.domain.order.repository;

import com.glassvue.domain.order.dto.OrderSearchCondition;
import com.glassvue.domain.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderRepositoryCustom {

    /**
     * 주문 동적 검색. 조건의 {@code memberId}가 스코프를 결정한다 —
     * 값이 있으면 그 회원 주문만, null이면 전체(관리자).
     */
    Page<Order> search(OrderSearchCondition condition, Pageable pageable);
}
