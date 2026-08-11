package com.glassvue.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassvue.domain.cart.dto.CartItemResponse;
import com.glassvue.domain.catalog.entity.StockChangeReason;
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
import com.glassvue.domain.order.event.OrderReturnRejectedEvent;
import com.glassvue.domain.order.event.OrderReturnedEvent;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
    // 취소·반품 때 쿠폰을 되돌리는 창구(2026-08-11). 결제 경로에서도 쓰이지만 스텁 없이 0/ null 이라
    // "쿠폰 안 씀"이 되어 기존 테스트엔 영향이 없다.
    @Mock com.glassvue.domain.coupon.service.CouponService couponService;
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
        return Order.create(memberId, "구매자닉", List.of(items), "수령인", "010-1234-5678", "06134", "서울시 강남구 테헤란로 1", "3층", null, 3_000, "20260101-0001", null, 0L, null, 0L);
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
        // ⚠ **조회 키를 주문의 실제 id 로 맞춘다**(2026-08-10). 여기 있던 `orderId`(별개의 randomUUID)는
        //    운영에서 나올 수 없는 스텁이었다 — findByIdAndMemberId(x) 가 id 가 x 가 **아닌** 주문을
        //    돌려주는 상황이다. id 를 앱에서 만들므로(BaseTimeEntity) Order.create 가 이미 자기 id 를 갖는다.
        //    B-25 리팩터가 재고 이력에 넘기는 값을 «요청 id» 에서 «엔티티 id» 로 바꾸자 이 어긋남이 드러났다.
        when(orderRepository.findByIdAndMemberId(order.getId(), memberId)).thenReturn(Optional.of(order));
        orderService.cancel(order.getId(), memberId, "단순 변심");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        // 언제 취소됐는지도 남긴다 — updated_at은 다른 변경에도 갱신돼 취소 시각이라 단정할 수 없다.
        assertThat(order.getCancelledAt()).isNotNull();
        // 취소 사유도 같은 자리에 남는다(B-17) — 반품에만 있던 것을 취소에도 뒀다.
        assertThat(order.getCancelReason()).isEqualTo("단순 변심");
        verify(productCommandService, times(1)).increaseStock(p1, 3, StockChangeReason.CANCEL, order.getId());
        verify(eventPublisher).publishEvent(any(OrderCancelledEvent.class));
    }

    /**
     * 🔴 <b>「되돌리는 것들」이 하나도 빠지지 않는가</b> (2026-08-11, 08-10 §16-4 3번).
     *
     * <p>이 자리는 <b>두 번 연속 걸렸다</b>: 2026-08-07 에 취소가 <b>적립금</b>을 안 돌려주고 있었고,
     * 그걸 고친 뒤에도 2026-08-11 에 <b>쿠폰</b>이 같은 이유로 빠져 있었다. 원인은 매번 같다 —
     * 되돌릴 것들이 한 줄로 모여 있지 않으면 새로 생긴 것이 조용히 누락된다.
     *
     * <p>⚠ 그래서 이 테스트는 <b>재고·적립금·쿠폰·이벤트를 한 곳에서 함께</b> 못 박는다. 갈라 두면
     * «쿠폰 테스트가 없어서 쿠폰이 빠졌다» 가 그대로 반복된다. <b>다음에 되돌릴 것이 생기면 여기에 줄이 는다.</b>
     */
    @Test
    @DisplayName("취소: 되돌리는 것 전부 — 재고 · 적립금 · **쿠폰** · 알림")
    void cancel_restoresEverything() {
        UUID p1 = UUID.randomUUID();
        UUID memberCouponId = UUID.randomUUID();
        Order order = Order.create(memberId, "구매자닉",
                List.of(OrderItem.of(p1, p1, null, "지바", null, 10_000, null, 3)),
                "수령인", "010-1234-5678", "06134", "서울시 강남구 테헤란로 1", "3층", null,
                3_000, "20260101-0009", "5천원 쿠폰", 5_000L, memberCouponId, 500L);
        when(orderRepository.findByIdAndMemberId(order.getId(), memberId)).thenReturn(Optional.of(order));

        orderService.cancel(order.getId(), memberId, "단순 변심");

        verify(productCommandService).increaseStock(p1, 3, StockChangeReason.CANCEL, order.getId());
        verify(pointService).refundCancelledOrder(memberId, 500L, order.getId());
        // 🔴 쿠폰 — 2026-08-11 이전에는 이 줄이 없었고, 그래서 고객이 쿠폰을 잃었다.
        verify(couponService).restore(memberCouponId);
        verify(eventPublisher).publishEvent(any(OrderCancelledEvent.class));
    }

    /**
     * 쿠폰을 안 쓴 주문도 <b>같은 경로</b>를 탄다 — 호출부가 «쿠폰 썼나» 로 갈라지지 않게
     * {@code CouponService.restore(null)} 가 받아 준다. 갈라 두면 그 분기가 다음 누락 자리가 된다.
     * ⚠ 대조군이다: 위 테스트만 있으면 «항상 restore 를 부른다» 로 고쳐도 통과한다.
     */
    @Test
    @DisplayName("취소: 쿠폰을 안 쓴 주문도 같은 경로 — restore(null) 이 간다 (분기를 만들지 않는다)")
    void cancel_noCoupon_stillSamePath() {
        Order order = sampleOrder(); // memberCouponId = null
        when(orderRepository.findByIdAndMemberId(order.getId(), memberId)).thenReturn(Optional.of(order));

        orderService.cancel(order.getId(), memberId, null);

        verify(couponService).restore(null);
    }

    /**
     * 🔴 <b>반품 승인은 취소의 짝이다</b> — 되돌리는 목록이 같아야 한다 (2026-08-11).
     *
     * <p>⚠ <b>이 경로에는 서비스 테스트가 아예 없었다.</b> 2026-08-07 사고가 «반품만 고쳐지고 취소는
     * 안 고쳐진» 비대칭이었는데, 정작 반품 쪽은 단위 테스트 없이 돌고 있었다 — 즉 <b>비대칭을 잡아 줄
     * 것이 어느 쪽에도 없었다.</b> 취소 쪽에 줄을 더할 때 여기도 같이 봐야 한다.
     *
     * <p>⚠ 환불액이 «상품합계 − 쿠폰» 인 것과 쿠폰 복구는 <b>앞뒤가 맞아야 한다</b>: 할인받은 만큼은
     * 돈으로 안 돌려주므로, 그 할인의 근거였던 쿠폰을 돌려줘야 고객이 손해를 안 본다.
     */
    @Test
    @DisplayName("반품 승인: 되돌리는 것 전부 — 재고(RETURN) · 적립금 · **쿠폰**")
    void approveReturn_restoresEverything() {
        UUID p1 = UUID.randomUUID();
        UUID memberCouponId = UUID.randomUUID();
        Order order = Order.create(memberId, "구매자닉",
                List.of(OrderItem.of(p1, p1, null, "지바", null, 10_000, null, 2)),
                "수령인", "010-1234-5678", "06134", "서울시 강남구 테헤란로 1", "3층", null,
                3_000, "20260101-0010", "5천원 쿠폰", 5_000L, memberCouponId, 0L);
        order.pay();
        order.ship(DeliveryCarrier.CJ, "123");
        order.deliver();
        order.requestReturn("ZZ-반품사유");
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        orderService.approveReturn(order.getId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.RETURNED);
        // 재고 이력은 취소와 **구분**된다(B-19) — 원장에서 «왜 돌아왔는지» 가 보여야 값이 있다.
        verify(productCommandService).increaseStock(p1, 2, StockChangeReason.RETURN, order.getId());
        verify(pointService).refundReturnedOrder(eq(memberId), eq(order.refundableAmount()),
                eq(order.getEarnedPoint()), eq(order.rewardableAmount()), eq(order.getId()));
        // 🔴 취소와 같은 줄 — 한쪽에만 넣으면 그게 다음 비대칭의 시작이다.
        verify(couponService).restore(memberCouponId);
        // 🔴 알림 (2026-08-11) — 이벤트 발행까지 봐야 한다. 2026-08-11 변형 M14 에서 «발행을 지워도
        //    아무도 안 잡는» 자리가 드러났다: 「되돌리는 것들」만 보고 **알리는 것**은 안 봤다.
        ArgumentCaptor<OrderReturnedEvent> returned = ArgumentCaptor.forClass(OrderReturnedEvent.class);
        verify(eventPublisher).publishEvent(returned.capture());
        assertThat(returned.getValue().memberId()).isEqualTo(memberId);
        // 환불액이 실려야 알림 문구가 «○○원이 환불되었습니다» 를 말할 수 있다(핸들러는 주문을 못 본다).
        assertThat(returned.getValue().refundedPoint()).isEqualTo(order.refundableAmount());
    }

    /**
     * 🔴 반품 <b>거절</b> — 재고·적립금·쿠폰은 안 건드리고 <b>알림만</b> 나간다 (2026-08-11).
     *
     * <p>⚠ <b>이 경로도 서비스 테스트가 없었다.</b> 거절은 상태가 조용히 {@code DELIVERED} 로
     * 되돌아갈 뿐이라, 알림이 빠지면 고객은 <b>요청해 놓고 영영 소식이 없다</b> —
     * 그런데 아무도 «알림 고장» 으로 신고하지 않는다(그런 알림이 있었다는 걸 모르니까).
     *
     * <p>⚠ 되돌리는 쪽을 <b>안 하는 것</b>도 함께 못 박는다: 승인 안 했으니 재고도 적립금도
     * 쿠폰도 그대로여야 한다. 안 그러면 «거절인데 환불되는» 상태가 된다.
     */
    @Test
    @DisplayName("반품 거절: 배송완료로 되돌리고 **알림만** 보낸다 (재고·적립금·쿠폰은 그대로)")
    void rejectReturn_notifiesOnly() {
        UUID p1 = UUID.randomUUID();
        Order order = Order.create(memberId, "구매자닉",
                List.of(OrderItem.of(p1, p1, null, "지바", null, 10_000, null, 2)),
                "수령인", "010-1234-5678", "06134", "서울시 강남구 테헤란로 1", "3층", null,
                3_000, "20260101-0011", "5천원 쿠폰", 5_000L, UUID.randomUUID(), 0L);
        order.pay();
        order.ship(DeliveryCarrier.CJ, "123");
        order.deliver();
        order.requestReturn("ZZ-반품사유");
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        orderService.rejectReturn(order.getId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        verify(eventPublisher).publishEvent(any(OrderReturnRejectedEvent.class));
        // 승인 안 했으니 되돌리는 것은 하나도 없어야 한다.
        verify(productCommandService, never()).increaseStock(any(), anyInt(), any(), any());
        verify(couponService, never()).restore(any());
    }

    @Test
    @DisplayName("취소: 발송된(SHIPPED) 주문 → ORDER_NOT_CANCELLABLE, 재고 복원 안 함")
    void cancel_shippedBlocked() {
        Order order = sampleOrder();
        order.pay();
        order.ship(DeliveryCarrier.CJ, "123");
        when(orderRepository.findByIdAndMemberId(orderId, memberId)).thenReturn(Optional.of(order));
        assertErrorCode(() -> orderService.cancel(orderId, memberId, "바로 취소"),
                ErrorCode.ORDER_NOT_CANCELLABLE);
        verify(productCommandService, never()).increaseStock(
                any(), org.mockito.ArgumentMatchers.anyLong(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    /**
     * 관리자 취소의 사유 가드는 <b>엔티티에도</b> 있다 (B-25, 2026-08-10).
     *
     * <p>⚠ <b>변형 주입에서 이 가드만 안 잡혔다.</b> HTTP 로는 {@code AdminOrderCancelRequest} 의
     * {@code @NotBlank} 가 먼저 400 을 내서 이 줄에 <b>닿지 않기 때문</b>이다 — 통합 테스트로는
     * 영원히 못 덮는 자리라 여기서 직접 부른다.
     *
     * <p>가드를 지우지 않고 남긴 이유: 이 메서드는 {@code cancelledBy} 를 채우는 <b>유일한 경로</b>이고,
     * 사유 없이 통과하면 «누가 취소했는지는 아는데 왜인지는 모르는» 행이 남는다. DTO 를 안 거치는
     * 호출부(배치·내부 호출)가 생기면 그때는 막을 것이 없다 — G-3 이 엔티티 생성자와 DB 제약
     * <b>양쪽</b>에 같은 규칙을 둔 것과 같은 판단(WA §2-4-2).
     */
    @Test
    @DisplayName("관리자 취소: 사유가 비면 엔티티가 막는다 — DTO 를 안 거치는 호출부 대비")
    void cancelByAdmin_blankReasonRejectedByEntity() {
        Order order = sampleOrder();
        UUID adminId = UUID.randomUUID();
        assertThatThrownBy(() -> order.cancelByAdmin("   ", adminId, "관리자"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> order.cancelByAdmin(null, adminId, "관리자"))
                .isInstanceOf(IllegalArgumentException.class);
        // ⚠ 거절됐으면 상태도 안 변해야 한다 — 사유만 막고 취소는 되면 최악이다.
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ORDERED);
        assertThat(order.getCancelledBy()).isNull();
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

    /**
     * ⚠ 2026-08-11 이전에는 이 테스트가 {@code Role.ADMIN} <b>하나만</b> 넣었고, 운영 코드는
     * {@code user.role() == Role.ADMIN} 이었다 — 즉 <b>테스트가 통과하는데 실제 운영 계정
     * (SUPER_ADMIN)은 남의 주문 상세에서 404 를 받고 있었다</b>(2026-08-10 §16-3).
     * 역할을 하나만 넣는 테스트는 <b>역할 경계를 지키지 못한다.</b> 관리자 역할 전부를 돌린다.
     *
     * <p>⚠ 반대편(USER 가 전체 조회로 새지 않는다)은 {@code get_user_scoped} 가 잡는다 —
     * 그게 없으면 «전부 findById» 로 고쳐도 이 테스트는 초록이다.
     */
    @ParameterizedTest(name = "{0} 은 전체 주문 조회")
    @EnumSource(value = Role.class, names = {"ADMIN", "SUPER_ADMIN"})
    @DisplayName("상세: 관리자는 전체 주문 조회(findById) — SUPER_ADMIN 포함")
    void get_admin_all(Role role) {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(sampleOrder()));
        AuthUser admin = new AuthUser(UUID.randomUUID(), role, "admin");
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

        verify(productCommandService).decreaseStock(eq(pid), eq(2L), any());
        verify(cartService).clear(memberId);
        verify(eventPublisher).publishEvent(any(OrderPlacedEvent.class));
    }

    @Test
    @DisplayName("결제: 재고 차감에 **그 주문의 id** 를 넘긴다 (재고 이력의 근거, B-19)")
    void checkout_passesOrderIdToStockDecrease() {
        UUID pid = UUID.randomUUID();
        when(cartService.getCart(memberId)).thenReturn(cartWith(availableItem(pid, 2)));
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        when(orderRepository.save(orderCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        orderService.checkout(buyer, SHIP);

        // any() 로는 **null 을 넘겨도 통과**한다 — 실제 주문 id 인지까지 봐야 이력이 근거를 갖는다.
        // 이 단언이 "차감을 주문 생성 뒤로 옮긴" 이유를 지킨다(적립금 이력과 같은 자리).
        ArgumentCaptor<UUID> orderIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(productCommandService).decreaseStock(eq(pid), eq(2L), orderIdCaptor.capture());
        assertThat(orderIdCaptor.getValue()).isEqualTo(orderCaptor.getValue().getId());
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
