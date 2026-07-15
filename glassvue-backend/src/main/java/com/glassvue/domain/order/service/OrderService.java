package com.glassvue.domain.order.service;

import com.glassvue.domain.cart.dto.CartItemResponse;
import com.glassvue.domain.cart.dto.CartResponse;
import com.glassvue.domain.cart.service.CartService;
import com.glassvue.domain.catalog.service.command.ProductCommandService;
import com.glassvue.domain.order.dto.OrderResponse;
import com.glassvue.domain.order.entity.Order;
import com.glassvue.domain.order.entity.OrderItem;
import com.glassvue.domain.order.entity.OrderStatus;
import com.glassvue.domain.order.repository.OrderRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
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

    @Transactional(readOnly = true)
    public List<OrderResponse> myOrders(UUID memberId) {
        return orderRepository.findByMemberIdOrderByCreatedAtDesc(memberId).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id, UUID memberId) {
        Order order = orderRepository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        return OrderResponse.from(order);
    }

    /** 주문 취소 — 본인 주문·ORDERED 상태만. 재고 복원. */
    @Transactional
    public void cancel(UUID id, UUID memberId) {
        Order order = orderRepository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (order.getStatus() != OrderStatus.ORDERED) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CANCELLABLE);
        }
        order.cancel();
        order.getItems().forEach(it -> productCommandService.increaseStock(it.getProductId(), it.getQuantity()));
        log.info("Order cancelled: {}", id);
    }
}
