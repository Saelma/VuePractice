package com.glassvue.domain.order.service;

import com.glassvue.domain.cart.dto.CartItemResponse;
import com.glassvue.domain.cart.dto.CartResponse;
import com.glassvue.domain.cart.service.CartService;
import com.glassvue.domain.catalog.service.command.ProductCommandService;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.order.dto.OrderResponse;
import com.glassvue.domain.order.entity.Order;
import com.glassvue.domain.order.entity.OrderItem;
import com.glassvue.domain.order.repository.OrderRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.security.AuthUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final ProductCommandService productCommandService;

    /** 장바구니 → 주문 생성. 재고 원자적 차감 + 카트 비우기. */
    @Transactional
    public UUID checkout(UUID memberId) {
        CartResponse cart = cartService.getCart(memberId);
        if (cart.items().isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }
        if (cart.items().stream().anyMatch(i -> !i.available())) {
            throw new BusinessException(ErrorCode.UNAVAILABLE_ITEM);
        }

        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItemResponse i : cart.items()) {
            productCommandService.decreaseStock(i.productId(), i.quantity()); // 부족하면 OUT_OF_STOCK
            orderItems.add(OrderItem.of(i.productId(), i.name(), i.price(), i.quantity()));
        }

        Order order = orderRepository.save(Order.create(memberId, orderItems));
        cartService.clear(memberId);
        log.info("Order created: {} by {}", order.getId(), memberId);
        return order.getId();
    }

    /** 회원이 해당 상품을 구매(취소되지 않은 주문)했는지 — 리뷰 도메인 등 타 도메인 공개 API. */
    @Transactional(readOnly = true)
    public boolean hasPurchased(UUID memberId, UUID productId) {
        return orderRepository.existsPurchase(memberId, productId);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> myOrders(UUID memberId) {
        return orderRepository.findByMemberIdOrderByCreatedAtDesc(memberId).stream()
                .map(OrderResponse::from)
                .toList();
    }

    /** 주문 상세 — 본인 주문만. 단, ADMIN은 발송 처리를 위해 전체 주문 조회 가능. */
    @Transactional(readOnly = true)
    public OrderResponse get(UUID id, AuthUser user) {
        Order order = (user.role() == Role.ADMIN
                ? orderRepository.findById(id)
                : orderRepository.findByIdAndMemberId(id, user.id()))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        return OrderResponse.from(order);
    }

    /** 결제 완료 처리 — 본인 주문·ORDERED만. 실제 결제는 이후 PG 연동으로 대체(지금은 상태 전이만). */
    @Transactional
    public void pay(UUID id, UUID memberId) {
        Order order = orderRepository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isPayable()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_PAYABLE);
        }
        order.pay();
        log.info("Order paid: {} by {}", id, memberId);
    }

    /** 발송 처리(관리자 전용, 권한은 SecurityConfig에서 강제) — PAID 상태만. */
    @Transactional
    public void ship(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isShippable()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_SHIPPABLE);
        }
        order.ship();
        log.info("Order shipped: {}", id);
    }

    /** 주문 취소 — 본인 주문·취소가능(ORDERED/PAID) 상태만. 재고 복원. */
    @Transactional
    public void cancel(UUID id, UUID memberId) {
        Order order = orderRepository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isCancellable()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CANCELLABLE);
        }
        order.cancel();
        order.getItems().forEach(it -> productCommandService.increaseStock(it.getProductId(), it.getQuantity()));
        log.info("Order cancelled: {}", id);
    }
}
