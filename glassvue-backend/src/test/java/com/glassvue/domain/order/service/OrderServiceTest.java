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
import com.glassvue.domain.order.dto.OrderCreateRequest;
import com.glassvue.domain.order.config.DeliveryProperties;
import com.glassvue.domain.order.entity.DeliveryCarrier;
import com.glassvue.domain.order.entity.Order;
import com.glassvue.domain.order.entity.OrderItem;
import com.glassvue.domain.order.entity.OrderStatus;
import com.glassvue.domain.order.event.OrderCancelledEvent;
import com.glassvue.domain.order.event.OrderPlacedEvent;
import com.glassvue.domain.order.repository.OrderRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.policy.ShippingPolicy;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.security.AuthUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock CartService cartService;
    @Mock ProductCommandService productCommandService;
    @Mock ApplicationEventPublisher eventPublisher;
    // 적립은 배송완료 때 **동기로** 일어난다(이벤트가 아니다) — 스텁 없이 0을 돌려주면 "적립 없음"이다.
    @Mock com.glassvue.domain.point.service.PointService pointService;
    // 정지 회원 주문 차단 가드(B-11 후속)용. 기본 false(활성)라 정상 주문 경로엔 영향 없다.
    @Mock com.glassvue.domain.member.service.MemberService memberService;
    // 설정 객체라 목이 아니라 실제 인스턴스를 넣는다 — 조회 링크 생성은 순수 문자열 조립이고,
    // 목으로 두면 null을 돌려줘 "링크가 안 만들어지는" 경로만 검증하게 된다.
    @Spy DeliveryProperties deliveryProperties = new DeliveryProperties();
    // 설정 객체라 목이 아니라 실제 인스턴스를 넣는다 — 배송비 계산은 순수 산술이고,
    // 목으로 두면 항상 0을 돌려줘 "배송비가 안 붙는" 경로만 검증하게 된다.
    @Spy ShippingPolicy shippingPolicy = new ShippingPolicy();
    @InjectMocks OrderService orderService;

    private final UUID memberId = UUID.randomUUID();
    private final AuthUser buyer = new AuthUser(memberId, Role.USER, "구매자닉");
    private final UUID orderId = UUID.randomUUID();

    private static final OrderCreateRequest SHIP = new OrderCreateRequest(
            "수령인", "010-1234-5678", "06134", "서울시 강남구 테헤란로 1", "3층", null, null, null);

    private Order orderWith(OrderItem... items) {
        return Order.create(memberId, "구매자닉", List.of(items), "수령인", "010-1234-5678", "06134", "서울시 강남구 테헤란로 1", "3층", null, 3_000, "20260101-0001", null, 0L, 0L);
    }
    private Order sampleOrder() {
        return orderWith(OrderItem.of(UUID.randomUUID(), UUID.randomUUID(), null, "지바", "/uploads/z_t.webp", 10_000, null, 2));
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
        orderService.ship(orderId, DeliveryCarrier.CJ, "123456789012");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        // 운송장은 발송과 한 트랜잭션이다 — "발송됐는데 추적 정보가 없는" 중간 상태를 만들지 않는다.
        assertThat(order.getShipCarrier()).isEqualTo(DeliveryCarrier.CJ);
        assertThat(order.getShipTrackingNo()).isEqualTo("123456789012");
    }

    @Test
    @DisplayName("발송: 미결제(ORDERED) 주문 → ORDER_NOT_SHIPPABLE")
    void ship_notShippable() {
        Order order = sampleOrder();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        assertErrorCode(() -> orderService.ship(orderId, DeliveryCarrier.CJ, "123"),
                ErrorCode.ORDER_NOT_SHIPPABLE);
    }

    @Test
    @DisplayName("배송완료: SHIPPED 주문 → DELIVERED + 수령 시각 기록")
    void deliver_success() {
        Order order = sampleOrder();
        order.pay();
        order.ship(DeliveryCarrier.CJ, "123456789012");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        orderService.deliver(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getDeliveredAt()).isNotNull();
    }

    @Test
    @DisplayName("배송완료: 미발송(PAID) 주문 → ORDER_NOT_DELIVERABLE")
    void deliver_notDeliverable() {
        Order order = sampleOrder();
        order.pay();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        assertErrorCode(() -> orderService.deliver(orderId), ErrorCode.ORDER_NOT_DELIVERABLE);
    }

    @Test
    @DisplayName("결제: 주문 품목에 **정가도 스냅샷**한다 (나중에 추가하면 과거 주문은 백필 불가)")
    void checkout_snapshotsListPrice() {
        UUID pid = UUID.randomUUID();
        // 39,000원짜리를 31,200원에 파는 상품(20% 할인)
        CartItemResponse discounted = new CartItemResponse(
                UUID.randomUUID(), pid, "지바", null, 31_200, 39_000L, ProductStatus.SELLING, 1, 31_200, true, null);
        when(cartService.getCart(memberId)).thenReturn(cartWith(discounted));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.checkout(buyer, SHIP);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        OrderItem item = captor.getValue().getItems().get(0);
        // 판매가는 실제로 낸 돈, 정가는 "그때 얼마에서 깎였는지"의 기록이다.
        assertThat(item.getPrice()).isEqualTo(31_200);
        assertThat(item.getListPrice()).isEqualTo(39_000L);
    }

    @Test
    @DisplayName("취소: ORDERED 주문 → CANCELLED + 아이템별 재고 복원")
    void cancel_restoresStock() {
        UUID p1 = UUID.randomUUID();
        Order order = orderWith(OrderItem.of(p1, p1, null, "지바", null, 10_000, null, 3));
        when(orderRepository.findByIdAndMemberId(orderId, memberId)).thenReturn(Optional.of(order));
        orderService.cancel(orderId, memberId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        // 언제 취소됐는지도 남긴다 — updated_at은 다른 변경에도 갱신돼 취소 시각이라 단정할 수 없다.
        assertThat(order.getCancelledAt()).isNotNull();
        verify(productCommandService, times(1)).increaseStock(p1, 3);
        verify(eventPublisher).publishEvent(any(OrderCancelledEvent.class));
    }

    @Test
    @DisplayName("취소: 발송된(SHIPPED) 주문 → ORDER_NOT_CANCELLABLE, 재고 복원 안 함")
    void cancel_shippedBlocked() {
        Order order = sampleOrder();
        order.pay();
        order.ship(DeliveryCarrier.CJ, "123");
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
        return new CartResponse(List.of(items), qty, price, 0, price, 0);
    }
    private CartItemResponse availableItem(UUID variantId, long qty) {
        // pid 자리에 variantId 를 넣는다 — checkout 이 이제 variantId 로 재고를 차감하므로 검증이 그걸 본다.
        return new CartItemResponse(UUID.randomUUID(), variantId, "지바", null, 10_000, null,
                ProductStatus.SELLING, qty, 10_000 * qty, true, "/uploads/z_t.webp");
    }

    @Test
    @DisplayName("결제(checkout): 재고 차감·카트 비움·OrderPlacedEvent 발행")
    void checkout_publishesEvent() {
        UUID pid = UUID.randomUUID();
        when(cartService.getCart(memberId)).thenReturn(cartWith(availableItem(pid, 2)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.checkout(buyer, SHIP);

        verify(productCommandService).decreaseStock(pid, 2);
        verify(cartService).clear(memberId);
        verify(eventPublisher).publishEvent(any(OrderPlacedEvent.class));
    }

    @Test
    @DisplayName("결제: 배송지를 주문에 **스냅샷**한다 (회원 기본 배송지를 참조하지 않는다)")
    void checkout_snapshotsShippingAddress() {
        UUID pid = UUID.randomUUID();
        when(cartService.getCart(memberId)).thenReturn(cartWith(availableItem(pid, 1)));
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        when(orderRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        orderService.checkout(buyer, SHIP);

        // 회원이 나중에 기본 배송지를 바꿔도 과거 주문은 "그때 보낸 곳"이어야 한다.
        Order saved = captor.getValue();
        assertThat(saved.getShipRecipient()).isEqualTo("수령인");
        assertThat(saved.getShipPhone()).isEqualTo("010-1234-5678");
        assertThat(saved.getShipZipcode()).isEqualTo("06134");
        assertThat(saved.getShipAddress1()).isEqualTo("서울시 강남구 테헤란로 1");
        assertThat(saved.getShipAddress2()).isEqualTo("3층");
    }

    @Test
    @DisplayName("결제: 주문 품목에 이름·가격과 함께 **이미지 URL도 스냅샷**한다")
    void checkout_snapshotsProductImage() {
        UUID pid = UUID.randomUUID();
        when(cartService.getCart(memberId)).thenReturn(cartWith(availableItem(pid, 2)));
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        when(orderRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        orderService.checkout(buyer, SHIP);

        // 상품이 바뀌거나 삭제돼도 주문 이력은 "그때 모습"이어야 하므로 참조가 아니라 스냅샷이다.
        assertThat(captor.getValue().getItems()).singleElement()
                .satisfies(i -> {
                    assertThat(i.getProductName()).isEqualTo("지바");
                    assertThat(i.getProductImageUrl()).isEqualTo("/uploads/z_t.webp");
                });
    }

    @Test
    @DisplayName("결제: 빈 장바구니 → CART_EMPTY, 이벤트/저장 없음")
    void checkout_emptyCart() {
        when(cartService.getCart(memberId)).thenReturn(cartWith());
        assertErrorCode(() -> orderService.checkout(buyer, SHIP), ErrorCode.CART_EMPTY);
        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("결제: 구매불가 상품 포함 → UNAVAILABLE_ITEM")
    void checkout_unavailable() {
        UUID pid = UUID.randomUUID();
        CartItemResponse soldOut = new CartItemResponse(UUID.randomUUID(), pid, "품절품", null, 10_000, null, ProductStatus.SOLD_OUT, 1, 10_000, false, null);
        when(cartService.getCart(memberId)).thenReturn(cartWith(soldOut));
        assertErrorCode(() -> orderService.checkout(buyer, SHIP), ErrorCode.UNAVAILABLE_ITEM);
        verify(eventPublisher, never()).publishEvent(any());
    }
}
