package com.glassvue.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassvue.domain.cart.dto.CartItemResponse;
import com.glassvue.domain.cart.dto.CartResponse;
import com.glassvue.domain.cart.service.CartService;
import com.glassvue.domain.catalog.entity.ProductStatus;
import com.glassvue.domain.catalog.service.command.ProductCommandService;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.order.entity.Order;
import com.glassvue.domain.order.entity.OrderItem;
import com.glassvue.domain.order.entity.OrderStatus;
import com.glassvue.domain.order.event.OrderCancelledEvent;
import com.glassvue.domain.order.event.OrderPlacedEvent;
import com.glassvue.domain.order.repository.OrderRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.security.AuthUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock CartService cartService;
    @Mock ProductCommandService productCommandService;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks OrderService orderService;

    private final UUID memberId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    private Order orderWith(OrderItem... items) {
        return Order.create(memberId, List.of(items));
    }
    private Order sampleOrder() {
        return orderWith(OrderItem.of(UUID.randomUUID(), "지바", 10_000, 2));
    }
    private static void assertErrorCode(Runnable r, ErrorCode expected) {
        assertThatThrownBy(r::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("결제: 본인 ORDERED 주문 → PAID")
    void pay_success() {
        Order order = sampleOrder();
        when(orderRepository.findByIdAndMemberId(orderId, memberId)).thenReturn(Optional.of(order));
        orderService.pay(orderId, memberId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("결제: 이미 결제된 주문 → ORDER_NOT_PAYABLE")
    void pay_notPayable() {
        Order order = sampleOrder();
        order.pay();
        when(orderRepository.findByIdAndMemberId(orderId, memberId)).thenReturn(Optional.of(order));
        assertErrorCode(() -> orderService.pay(orderId, memberId), ErrorCode.ORDER_NOT_PAYABLE);
    }

    @Test
    @DisplayName("결제: 없는 주문 → ORDER_NOT_FOUND")
    void pay_notFound() {
        when(orderRepository.findByIdAndMemberId(orderId, memberId)).thenReturn(Optional.empty());
        assertErrorCode(() -> orderService.pay(orderId, memberId), ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("발송: PAID 주문 → SHIPPED")
    void ship_success() {
        Order order = sampleOrder();
        order.pay();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        orderService.ship(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    @DisplayName("발송: 미결제(ORDERED) 주문 → ORDER_NOT_SHIPPABLE")
    void ship_notShippable() {
        Order order = sampleOrder();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        assertErrorCode(() -> orderService.ship(orderId), ErrorCode.ORDER_NOT_SHIPPABLE);
    }

    @Test
    @DisplayName("취소: ORDERED 주문 → CANCELLED + 아이템별 재고 복원")
    void cancel_restoresStock() {
        UUID p1 = UUID.randomUUID();
        Order order = orderWith(OrderItem.of(p1, "지바", 10_000, 3));
        when(orderRepository.findByIdAndMemberId(orderId, memberId)).thenReturn(Optional.of(order));
        orderService.cancel(orderId, memberId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(productCommandService, times(1)).increaseStock(p1, 3);
        verify(eventPublisher).publishEvent(any(OrderCancelledEvent.class));
    }

    @Test
    @DisplayName("취소: 발송된(SHIPPED) 주문 → ORDER_NOT_CANCELLABLE, 재고 복원 안 함")
    void cancel_shippedBlocked() {
        Order order = sampleOrder();
        order.pay();
        order.ship();
        when(orderRepository.findByIdAndMemberId(orderId, memberId)).thenReturn(Optional.of(order));
        assertErrorCode(() -> orderService.cancel(orderId, memberId), ErrorCode.ORDER_NOT_CANCELLABLE);
        verify(productCommandService, never()).increaseStock(any(), org.mockito.ArgumentMatchers.anyLong());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("상세: 일반 사용자는 본인 주문만(findByIdAndMemberId)")
    void get_user_scoped() {
        when(orderRepository.findByIdAndMemberId(orderId, memberId)).thenReturn(Optional.of(sampleOrder()));
        AuthUser user = new AuthUser(memberId, Role.USER, "kim");
        orderService.get(orderId, user);
        verify(orderRepository).findByIdAndMemberId(orderId, memberId);
        verify(orderRepository, never()).findById(any());
    }

    @Test
    @DisplayName("상세: 관리자는 전체 주문 조회(findById)")
    void get_admin_all() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(sampleOrder()));
        AuthUser admin = new AuthUser(UUID.randomUUID(), Role.ADMIN, "admin");
        orderService.get(orderId, admin);
        verify(orderRepository).findById(orderId);
        verify(orderRepository, never()).findByIdAndMemberId(any(), any());
    }

    @Test
    @DisplayName("구매 여부는 리포지토리에 위임")
    void hasPurchased_delegates() {
        UUID pid = UUID.randomUUID();
        when(orderRepository.existsPurchase(memberId, pid)).thenReturn(true);
        assertThat(orderService.hasPurchased(memberId, pid)).isTrue();
    }

    private CartResponse cartWith(CartItemResponse... items) {
        long qty = 0, price = 0;
        for (CartItemResponse i : items) { qty += i.quantity(); price += i.lineTotal(); }
        return new CartResponse(List.of(items), qty, price);
    }
    private CartItemResponse availableItem(UUID pid, long qty) {
        return new CartItemResponse(pid, "지바", 10_000, ProductStatus.SELLING, qty, 10_000 * qty, true);
    }

    @Test
    @DisplayName("결제(checkout): 재고 차감·카트 비움·OrderPlacedEvent 발행")
    void checkout_publishesEvent() {
        UUID pid = UUID.randomUUID();
        when(cartService.getCart(memberId)).thenReturn(cartWith(availableItem(pid, 2)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.checkout(memberId);

        verify(productCommandService).decreaseStock(pid, 2);
        verify(cartService).clear(memberId);
        verify(eventPublisher).publishEvent(any(OrderPlacedEvent.class));
    }

    @Test
    @DisplayName("결제: 빈 장바구니 → CART_EMPTY, 이벤트/저장 없음")
    void checkout_emptyCart() {
        when(cartService.getCart(memberId)).thenReturn(cartWith());
        assertErrorCode(() -> orderService.checkout(memberId), ErrorCode.CART_EMPTY);
        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("결제: 구매불가 상품 포함 → UNAVAILABLE_ITEM")
    void checkout_unavailable() {
        UUID pid = UUID.randomUUID();
        CartItemResponse soldOut = new CartItemResponse(pid, "품절품", 10_000, ProductStatus.SOLD_OUT, 1, 10_000, false);
        when(cartService.getCart(memberId)).thenReturn(cartWith(soldOut));
        assertErrorCode(() -> orderService.checkout(memberId), ErrorCode.UNAVAILABLE_ITEM);
        verify(eventPublisher, never()).publishEvent(any());
    }
}
