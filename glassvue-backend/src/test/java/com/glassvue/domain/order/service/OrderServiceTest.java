package com.glassvue.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
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
import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.domain.audit.event.AdminActionEvent;
import com.glassvue.domain.order.event.OrderCancelledEvent;
import com.glassvue.domain.order.event.OrderPlacedEvent;
import com.glassvue.domain.order.event.OrderReturnRejectedEvent;
import com.glassvue.domain.order.event.OrderItemCancelledEvent;
import com.glassvue.domain.order.event.OrderReturnRequestedEvent;
import com.glassvue.domain.order.event.OrderReturnedEvent;
import com.glassvue.domain.order.repository.OrderRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.policy.ShippingPolicy;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.security.AuthUser;
import java.util.List;
import java.util.Map;
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
    // 관리자 주문 조작 넷에 행위자가 붙었다 (2026-08-14, V51) — 원장에 «누가» 를 남기기 위해서다.
    private final AuthUser admin = new AuthUser(UUID.randomUUID(), Role.ADMIN, "ZZ관리자");
    private final UUID orderId = UUID.randomUUID();

    private static final OrderCreateRequest SHIP = new OrderCreateRequest(
            "수령인", "010-1234-5678", "06134", "서울시 강남구 테헤란로 1", "3층", null, null, null);

    private Order orderWith(OrderItem... items) {
        return Order.create(memberId, "구매자닉", List.of(items), "수령인", "010-1234-5678", "06134", "서울시 강남구 테헤란로 1", "3층", null, 3_000, "20260101-0001", null, 0L, null, 0L);
    }
    private Order sampleOrder() {
        return orderWith(OrderItem.of(UUID.randomUUID(), UUID.randomUUID(), null, "지바", "/uploads/z_t.webp", 10_000, 10_000L, null, 2));
    }
    /**
     * 발행된 이벤트 중 <b>그 타입인 것 하나</b>를 집는다.
     *
     * <p>⚠ 왜 필요한가: 한 조작이 <b>이벤트를 둘 이상</b> 발행하게 되면서(알림 + 감사 원장, 2026-08-14)
     * {@code verify(publisher).publishEvent(captor.capture())} 가 깨진다 — 캡터는 타입을 안 가리므로
     * 그 문장은 «publishEvent 가 정확히 한 번» 을 요구한다. <b>테스트가 발행 개수에 묶여 있었던 것</b>이고,
     * 그건 검증하려던 바가 아니다(«그 알림이 나갔나» 이지 «그것 말고 아무것도 안 나갔나» 가 아니다).
     */
    private <T> T capturePublished(Class<T> type) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues().stream()
                .filter(type::isInstance).map(type::cast).findFirst()
                .orElseThrow(() -> new AssertionError(type.getSimpleName() + " 가 발행되지 않았다"));
    }

    /** 원장에 실린 줄. 없으면 실패한다. */
    private AdminActionEvent captureAudit() {
        return capturePublished(AdminActionEvent.class);
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
        orderService.ship(orderId, admin, DeliveryCarrier.CJ, "123456789012");
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
        assertErrorCode(() -> orderService.ship(orderId, admin, DeliveryCarrier.CJ, "123"),
                ErrorCode.ORDER_NOT_SHIPPABLE);
        // 🔴 거부됐으면 원장에도 없어야 한다 — «조작 없이 감사 없다».
        verify(eventPublisher, never()).publishEvent(any(AdminActionEvent.class));
    }

    @Test
    @DisplayName("배송완료: SHIPPED 주문 → DELIVERED + 수령 시각 기록")
    void deliver_success() {
        Order order = sampleOrder();
        order.pay();
        order.ship(DeliveryCarrier.CJ, "123456789012");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        orderService.deliver(orderId, admin);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getDeliveredAt()).isNotNull();
    }

    @Test
    @DisplayName("배송완료: 미발송(PAID) 주문 → ORDER_NOT_DELIVERABLE")
    void deliver_notDeliverable() {
        Order order = sampleOrder();
        order.pay();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        assertErrorCode(() -> orderService.deliver(orderId, admin), ErrorCode.ORDER_NOT_DELIVERABLE);
        verify(eventPublisher, never()).publishEvent(any(AdminActionEvent.class));
    }

    @Test
    @DisplayName("결제: 주문 품목에 **정가도 스냅샷**한다 (나중에 추가하면 과거 주문은 백필 불가)")
    void checkout_snapshotsListPrice() {
        UUID pid = UUID.randomUUID();
        // 정가 39,000 을 걸어 두고 31,200 에 파는 상품. ⚠ **기간 세일이 아니다** —
        //    관리자가 판매가를 31,200 으로 정해 둔 것이라 «세일 전 판매가» 도 31,200 이다.
        CartItemResponse discounted = new CartItemResponse(
                UUID.randomUUID(), pid, "지바", null, 31_200, 31_200, 39_000L,
                ProductStatus.SELLING, 1, 31_200, true, null);
        when(cartService.getCart(memberId)).thenReturn(cartWith(discounted));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.checkout(buyer, SHIP);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        OrderItem item = captor.getValue().getItems().get(0);
        // 판매가는 실제로 낸 돈, 정가는 "그때 얼마에서 깎였는지"의 기록이다.
        assertThat(item.getPrice()).isEqualTo(31_200);
        assertThat(item.getListPrice()).isEqualTo(39_000L);
        // 🔴 **세 값이 다른 것을 여기서 못 박는다**(G-9). 기간 세일이 없으므로 세일 전 판매가는
        //    판매가와 같고, 정가만 따로 39,000 이다. 셋을 한 칸으로 합치면 이 구분이 사라진다.
        assertThat(item.getRegularPrice()).isEqualTo(31_200L);
    }

    @Test
    @DisplayName("🔴 결제: **세일 전 판매가**도 스냅샷한다 — 상품이 지워져도 «얼마에서 깎였나» 가 남는다 (G-9)")
    void checkout_snapshotsRegularPrice() {
        // 2026-08-20 실측 표본과 같은 모양: 정가 칸은 **비어 있고**(관리자가 안 넣었다)
        // 기간 세일 20% 가 걸려 12,000 → 9,600 에 팔린다.
        UUID pid = UUID.randomUUID();
        CartItemResponse onSale = new CartItemResponse(
                UUID.randomUUID(), pid, "ZZ-세일품", "L", 9_600, 12_000, null,
                ProductStatus.SELLING, 1, 9_600, true, null);
        when(cartService.getCart(memberId)).thenReturn(cartWith(onSale));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.checkout(buyer, SHIP);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        OrderItem item = captor.getValue().getItems().get(0);
        assertThat(item.getPrice()).isEqualTo(9_600);
        // 🔴 **이 단언이 G-9 의 본체다.** 실측(2026-08-20, 20260820-4733)에서는 여기가 통째로 없어
        //    9,600 만 남고 «원래 12,000 이었다» 가 사라졌다.
        assertThat(item.getRegularPrice()).isEqualTo(12_000L);
        // ⚠ 정가 칸이 비어 있어도 세일 흔적은 남는다 — 그게 list_price 를 재활용하지 않은 이유다.
        assertThat(item.getListPrice()).isNull();
    }

    @Test
    @DisplayName("취소: ORDERED 주문 → CANCELLED + 아이템별 재고 복원")
    void cancel_restoresStock() {
        UUID p1 = UUID.randomUUID();
        Order order = orderWith(OrderItem.of(p1, p1, null, "지바", null, 10_000, 10_000L, null, 3));
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
                List.of(OrderItem.of(p1, p1, null, "지바", null, 10_000, 10_000L, null, 3)),
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
                List.of(OrderItem.of(p1, p1, null, "지바", null, 10_000, 10_000L, null, 2)),
                "수령인", "010-1234-5678", "06134", "서울시 강남구 테헤란로 1", "3층", null,
                3_000, "20260101-0010", "5천원 쿠폰", 5_000L, memberCouponId, 0L);
        order.pay();
        order.ship(DeliveryCarrier.CJ, "123");
        order.deliver();
        requestFullReturn(order, "ZZ-반품사유");
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        orderService.approveReturn(order.getId(), admin);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.RETURNED);
        // 재고 이력은 취소와 **구분**된다(B-19) — 원장에서 «왜 돌아왔는지» 가 보여야 값이 있다.
        verify(productCommandService).increaseStock(p1, 2, StockChangeReason.RETURN, order.getId());
        // 🔴 **글자 그대로 숫자를 적는다** (2026-08-25, G-10). 예전엔 `order.refundableAmount()` 를
        //    호출 **뒤에** 다시 읽어 기대값으로 썼는데, 그 값들은 이제 반품이 반영돼 **전부 0** 이다 —
        //    즉 «0 을 넘겼는지» 를 «0 과» 비교하는 테스트라 무엇을 넘기든 통과했을 자리다.
        //    ⚠ 엔티티에서 기대값을 다시 읽으면 그 테스트는 코드와 함께 낡는다(WA §1-2-1 세 번째 줄).
        //    상품 20,000 − 쿠폰 5,000 = 환불 15,000 · 적립은 아직 0 · 등급 기준도 15,000.
        verify(pointService).refundReturnedOrder(memberId, 15_000L, 0L, 15_000L, order.getId());
        // 🔴 취소와 같은 줄 — 한쪽에만 넣으면 그게 다음 비대칭의 시작이다.
        verify(couponService).restore(memberCouponId);
        // 🔴 알림 (2026-08-11) — 이벤트 발행까지 봐야 한다. 2026-08-11 변형 M14 에서 «발행을 지워도
        //    아무도 안 잡는» 자리가 드러났다: 「되돌리는 것들」만 보고 **알리는 것**은 안 봤다.
        // ⚠ 발행이 **둘**이 됐다(알림 + 원장, 2026-08-14) — 캡터는 타입을 안 가리므로
        //    verify(...).publishEvent(capture()) 는 «정확히 한 번» 을 요구해 깨진다. 타입으로 고른다.
        OrderReturnedEvent returnedEvent = capturePublished(OrderReturnedEvent.class);
        assertThat(returnedEvent.memberId()).isEqualTo(memberId);
        // 환불액이 실려야 알림 문구가 «○○원이 환불되었습니다» 를 말할 수 있다(핸들러는 주문을 못 본다).
        // 🔴 여기도 숫자를 그대로 적는다 — `order.refundableAmount()` 는 승인 뒤 0 이라 자기 자신과
        //    비교하는 셈이 된다(위 refundReturnedOrder 단언과 같은 이유, 2026-08-25).
        assertThat(returnedEvent.refundedPoint()).isEqualTo(15_000L);
        // 🔴 판매량 되돌림은 **이번에 반품된 수량**이어야 한다 (G-10) — 예전엔 원본 수량을 실었다.
        //    전량 반품이면 둘이 같지만, 부분이 생기면 갈린다(WA §1-2-1: 목록이 맞아도 «양»이 틀린다).
        assertThat(returnedEvent.lines()).singleElement()
                .satisfies(line -> {
                    assertThat(line.productId()).isEqualTo(p1);
                    assertThat(line.quantity()).isEqualTo(2);
                });
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
                List.of(OrderItem.of(p1, p1, null, "지바", null, 10_000, 10_000L, null, 2)),
                "수령인", "010-1234-5678", "06134", "서울시 강남구 테헤란로 1", "3층", null,
                3_000, "20260101-0011", "5천원 쿠폰", 5_000L, UUID.randomUUID(), 0L);
        order.pay();
        order.ship(DeliveryCarrier.CJ, "123");
        order.deliver();
        requestFullReturn(order, "ZZ-반품사유");
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        orderService.rejectReturn(order.getId(), admin, "ZZ-거절사유");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);

        // 🔴 거절은 상태를 안 남긴다(DELIVERED 로 돌아간다) — 그래서 **이 둘이 「거절이 있었다」의
        //    유일한 증거**이고, 화면의 반품 카드도 이걸로 뜬다(V47, 2026-08-11).
        assertThat(order.getReturnRejectedReason()).isEqualTo("ZZ-거절사유");
        assertThat(order.getReturnRejectedAt()).isNotNull();
        // ⚠ 요청 시각을 **지우지 않는다** — 예전엔 NULL 로 지워서 «언제 요청했나» 가 사라졌다.
        assertThat(order.getReturnRequestedAt())
                .as("요청 시각이 남아야 «언제 요청해서 언제 거절됐나» 가 읽힌다")
                .isNotNull();

        OrderReturnRejectedEvent rejectedEvent = capturePublished(OrderReturnRejectedEvent.class);
        // 사유가 이벤트에 실려야 알림 문구가 «왜 거절됐는지» 를 말할 수 있다.
        assertThat(rejectedEvent.reason()).isEqualTo("ZZ-거절사유");

        // 승인 안 했으니 되돌리는 것은 하나도 없어야 한다.
        verify(productCommandService, never()).increaseStock(any(), anyInt(), any(), any());
        verify(couponService, never()).restore(any());
    }

    // ── 관리자 주문 조작을 원장에 남긴다 (2026-08-14, V51) ──────────────────────────
    //
    // 🔴 **넷 다 「언제」는 주문에 남고 「누가」는 아무 데도 안 남았다.** 행위자 컬럼을 가진 것은
    //    취소(cancelledBy) 하나뿐이었고 — 그게 감사도 함께 붙어 있던 자리다.
    //    돈이 나가는 조작(배송완료 적립 · 반품 환불)인데도 «누가 승인했나» 를 물을 방법이 없었다.
    //
    // ⚠ **대상은 «주문» 이 아니라 «주문자» 다** — 감사 테이블의 target 은 회원이다(V43 과 같은 모양).
    //    상품(V50)이 부딪힌 «대상에 회원이 없다» 가 여기서는 안 생긴다.

    @Test
    @DisplayName("발송: 원장에 **누가 · 어느 주문 · 어느 택배사/송장** 이 남는다")
    void ship_recordsAudit() {
        Order order = sampleOrder();
        order.pay();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(memberService.loginIdOf(memberId)).thenReturn("zzbuyer");

        orderService.ship(orderId, admin, DeliveryCarrier.CJ, "123456789012");

        AdminActionEvent e = captureAudit();
        assertThat(e.action()).isEqualTo(AuditAction.ORDER_SHIP);
        assertThat(e.actorId()).isEqualTo(admin.id());
        assertThat(e.actorName()).isEqualTo("ZZ관리자");
        // 대상은 주문자다 — 주문 자체는 detail 의 주문번호가 가리킨다.
        assertThat(e.targetId()).isEqualTo(memberId);
        assertThat(e.targetLogin()).isEqualTo("zzbuyer");
        assertThat(e.detail())
                .as("원장만 보고 «어느 주문을 어디로 보냈나» 가 읽혀야 한다")
                .isEqualTo(order.getOrderNo() + " / CJ대한통운 123456789012");
    }

    /**
     * 🔴 배송완료는 <b>적립금이 나간다</b>({@code earnOnDelivery}) — 그래서 원장에 <b>나간 액수</b>를 적는다.
     *
     * <p>⚠ 뱃지는 neutral 이다(모든 주문이 거치는 정상 진행이라 danger 로 칠하면 원장 절반이 빨개진다).
     * <b>색이 아니라 값으로 읽게 했다</b> — 그 값이 여기 detail 이다.
     */
    @Test
    @DisplayName("배송완료: 원장에 **나간 적립금**이 남는다 (색이 아니라 값으로 읽는다)")
    void deliver_recordsEarnedPoint() {
        Order order = sampleOrder();
        order.pay();
        order.ship(DeliveryCarrier.CJ, "123");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(memberService.loginIdOf(memberId)).thenReturn("zzbuyer");
        when(pointService.earnOnDelivery(eq(memberId), anyLong(), eq(orderId))).thenReturn(420L);

        orderService.deliver(orderId, admin);

        AdminActionEvent e = captureAudit();
        assertThat(e.action()).isEqualTo(AuditAction.ORDER_DELIVER);
        assertThat(e.detail()).isEqualTo(order.getOrderNo() + " / 적립 420P");
    }

    @Test
    @DisplayName("반품 승인: 원장에 **환불액**이 남는다 — 돈이 나간 조작이다")
    void approveReturn_recordsRefund() {
        Order order = returnRequestedOrder("20260101-0020");
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(memberService.loginIdOf(memberId)).thenReturn("zzbuyer");

        orderService.approveReturn(order.getId(), admin);

        AdminActionEvent e = captureAudit();
        assertThat(e.action()).isEqualTo(AuditAction.ORDER_RETURN_APPROVE);
        // 🔴 **무엇을 몇 개** 가 함께 남는다 (2026-08-25, G-10) — 부분 반품이 생기면서 금액만으로는
        //    «어느 품목이 빠졌나» 를 못 되짚는다(ORDER_ITEM_CANCEL 이 같은 자리에서 정한 것).
        assertThat(e.detail()).isEqualTo("20260101-0020 / 지바 2개 반품 / 환불 20000원");
    }

    /**
     * 🔴 거절 사유는 {@code return_rejected_reason} 에도 남지만 <b>그건 현재 상태</b>다 —
     * 고객이 다시 반품을 요청하면 {@code requestReturn} 이 그 칸을 null 로 지운다
     * ({@code requestReturn_clearsPreviousRejection} 이 그 동작을 못 박고 있다).
     * 즉 «거절이 있었다» 는 사실이 화면에서 사라지고, <b>그때 남는 곳이 원장뿐</b>이다.
     * 같은 정보의 중복이 아니라 <b>상태와 이력</b>이다.
     */
    @Test
    @DisplayName("반품 거절: 원장에 **사유**가 남는다 — 재요청이 오면 주문의 사유 칸은 지워진다")
    void rejectReturn_recordsReasonThatOrderWillLose() {
        Order order = returnRequestedOrder("20260101-0021");
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(memberService.loginIdOf(memberId)).thenReturn("zzbuyer");

        orderService.rejectReturn(order.getId(), admin, "ZZ-사용 흔적이 있습니다");

        AdminActionEvent e = captureAudit();
        assertThat(e.action()).isEqualTo(AuditAction.ORDER_RETURN_REJECT);
        assertThat(e.detail()).isEqualTo("20260101-0021 / ZZ-사용 흔적이 있습니다");

        // 🔴 여기가 요점이다: 재요청이 주문의 사유를 지워도 **원장의 줄은 그대로다**.
        requestFullReturn(order, "ZZ-그래도 반품해 주세요");
        assertThat(order.getReturnRejectedReason()).isNull();
        assertThat(e.detail()).contains("ZZ-사용 흔적이 있습니다");
    }

    /** 반품 요청까지 온 주문 하나. */
    private Order returnRequestedOrder(String orderNo) {
        UUID p1 = UUID.randomUUID();
        Order order = Order.create(memberId, "구매자닉",
                List.of(OrderItem.of(p1, p1, null, "지바", null, 10_000, 10_000L, null, 2)),
                "수령인", "010-1234-5678", "06134", "서울시 강남구 테헤란로 1", "3층", null,
                3_000, orderNo, null, 0L, null, 0L);
        order.pay();
        order.ship(DeliveryCarrier.CJ, "123");
        order.deliver();
        requestFullReturn(order, "ZZ-반품사유");
        return order;
    }

    /**
     * ⚠ 거절당한 뒤 <b>다시 요청</b>할 수 있다(거절이 {@code DELIVERED} 로 되돌리므로).
     * 그때 이전 거절 기록을 지우지 않으면 화면에 «요청됨» 과 «거절됨» 이 <b>동시에</b> 떠서
     * 어느 쪽이 지금인지 알 수 없게 된다(2026-08-11).
     */
    @Test
    @DisplayName("거절 후 재요청하면 이전 거절 기록이 지워진다 (두 상태가 겹쳐 보이지 않게)")
    void requestReturn_clearsPreviousRejection() {
        Order order = sampleOrder();
        order.pay();
        order.ship(DeliveryCarrier.CJ, "123");
        order.deliver();
        requestFullReturn(order, "ZZ-첫요청");
        order.rejectReturn("ZZ-거절사유");
        assertThat(order.getReturnRejectedReason()).isNotNull();   // 전제 확인

        requestFullReturn(order, "ZZ-다시요청");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.RETURN_REQUESTED);
        assertThat(order.getReturnReason()).isEqualTo("ZZ-다시요청");
        assertThat(order.getReturnRejectedReason()).isNull();
        assertThat(order.getReturnRejectedAt()).isNull();
    }

    /**
     * 🔴 반품 <b>요청</b>도 이벤트를 낸다 (2026-08-12, 08-11 이월).
     *
     * <p>⚠ <b>이 자리가 셋 중 마지막이었다.</b> 승인·거절은 2026-08-11 에 붙였는데 요청은
     * 범위 밖이었고, 그래서 <b>관리자가 화면을 봐야만</b> 반품이 들어온 걸 알았다
     * (재고 부족은 알림이 가는데 반품 승인 대기는 안 갔다).
     *
     * <p>⚠ 앞의 둘과 <b>방향이 반대</b>라 실리는 값도 다르다: 받는 쪽이 관리자라
     * <b>이벤트가 대상을 모른다.</b> 대신 «어느 주문인지 · 누가 요청했는지 · 왜» 를 싣는다 —
     * 핸들러는 주문을 못 보므로 여기서 안 실으면 알림 문구가 <b>«반품 요청이 있습니다» 뿐</b>이 된다.
     */
    @Test
    @DisplayName("🔴 반품 요청: 관리자 알림용 이벤트를 낸다 (주문번호·요청자·사유가 실린다)")
    void requestReturn_publishesEventForAdmins() {
        Order order = sampleOrder();
        order.pay();
        order.ship(DeliveryCarrier.CJ, "123");
        order.deliver();
        when(orderRepository.findByIdAndMemberId(orderId, memberId)).thenReturn(Optional.of(order));

        orderService.requestReturn(orderId, memberId, "ZZ-사이즈가 안 맞아요", fullReturnOf(order));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.RETURN_REQUESTED);

        ArgumentCaptor<OrderReturnRequestedEvent> requested =
                ArgumentCaptor.forClass(OrderReturnRequestedEvent.class);
        verify(eventPublisher).publishEvent(requested.capture());
        OrderReturnRequestedEvent event = requested.getValue();
        assertThat(event.orderId()).isEqualTo(order.getId());
        assertThat(event.reason()).isEqualTo("ZZ-사이즈가 안 맞아요");
        // 아래 둘이 없으면 알림이 «어느 주문인지 · 누가» 를 말할 수 없다 — 핸들러는 주문을 못 본다.
        assertThat(event.orderNo()).isEqualTo(order.getOrderNo());
        assertThat(event.buyerNickname()).isEqualTo(order.getBuyerNickname());
    }

    /**
     * ⚠ <b>요청이 거부되면 이벤트도 없어야 한다.</b> 배송완료가 아닌 주문에 반품을 걸면
     * {@code ORDER_NOT_RETURNABLE} 인데, 이때 알림이 나가면 <b>관리자가 있지도 않은 반품을 보러 간다.</b>
     */
    @Test
    @DisplayName("⚠ 대조군: 반품이 불가능한 주문이면 이벤트를 내지 않는다")
    void requestReturn_blocked_publishesNothing() {
        Order order = sampleOrder();
        order.pay(); // 아직 배송완료가 아니다
        when(orderRepository.findByIdAndMemberId(orderId, memberId)).thenReturn(Optional.of(order));

        assertErrorCode(() -> orderService.requestReturn(orderId, memberId, "ZZ-사유", fullReturnOf(order)),
                ErrorCode.ORDER_NOT_RETURNABLE);

        verify(eventPublisher, never()).publishEvent(any());
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
        return new CartItemResponse(UUID.randomUUID(), variantId, "지바", null, 10_000, 10_000, null,
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
        CartItemResponse soldOut = new CartItemResponse(UUID.randomUUID(), pid, "품절품", null,
                10_000, 10_000, null, ProductStatus.SOLD_OUT, 1, 10_000, false, null);
        when(cartService.getCart(memberId)).thenReturn(cartWith(soldOut));
        assertErrorCode(() -> orderService.checkout(buyer, SHIP), ErrorCode.UNAVAILABLE_ITEM);
        verify(eventPublisher, never()).publishEvent(any());
    }

    /**
     * 전량 반품 요청 — 부분 반품(2026-08-25, G-10)이 생기면서 <b>«무엇을 몇 개»를 말해야</b> 한다.
     *
     * <p>⚠ <b>«비면 전량» 같은 기본값을 두지 않았다</b>(G-10 결정 2 주석) — 그래서 옛 호출부가
     * 전부 컴파일 에러로 드러났고, 여기서 «전량» 이라고 <b>명시</b>하게 됐다.
     */
    private static void requestFullReturn(Order order, String reason) {
        order.requestReturn(reason, order.getItems().stream()
                .collect(java.util.stream.Collectors.toMap(OrderItem::getId, OrderItem::remainingQuantity)));
    }

    /** 서비스 호출용 — 남은 수량 전부를 담은 요청 맵. */
    private static java.util.Map<UUID, Long> fullReturnOf(Order order) {
        return order.getItems().stream()
                .collect(java.util.stream.Collectors.toMap(OrderItem::getId, OrderItem::remainingQuantity));
    }

    // ─────────────────── 부분 반품 (2026-08-25, BACKLOG G-10) ───────────────────

    /** 배송완료까지 밀고 적립을 스냅샷한 주문 하나 — 반품은 그 적립을 회수한다. */
    private Order deliveredOrder(UUID productId, long unitPrice, long qty, long earned) {
        Order order = orderWith(OrderItem.of(productId, productId, null, "지바", null,
                unitPrice, unitPrice, null, qty));
        order.pay();
        order.ship(DeliveryCarrier.CJ, "123");
        order.deliver();
        order.recordEarnedPoint(earned);
        return order;
    }

    /**
     * 🔴 <b>부분 반품 승인 — 되돌리는 것이 «요청된 몫만»이다.</b>
     *
     * <p>⚠ 예전 코드는 «남은 수량 전부» 를 복원하고 {@code refundableAmount()} 전액을 환불했다.
     * 전량 반품뿐이던 시절엔 그게 같은 값이었지만 <b>부분이 생기면 갈린다</b> — 1개만 요청했는데
     * 2개가 창고로 돌아가고 돈도 두 배로 나간다. 2026-08-24 사고와 <b>정확히 같은 모양</b>이다.
     */
    @Test
    @DisplayName("🔴 부분 반품 승인: 요청한 1개만 재고 복원 · 그 몫만 환불 · **쿠폰은 안 돌려준다**")
    void approvePartialReturn_onlyRequestedShare() {
        UUID p1 = UUID.randomUUID();
        UUID memberCouponId = UUID.randomUUID();
        Order order = Order.create(memberId, "구매자닉",
                List.of(OrderItem.of(p1, p1, null, "지바", null, 10_000, 10_000L, null, 2)),
                "수령인", "010-1234-5678", "06134", "서울시 강남구 테헤란로 1", "3층", null,
                0, "20260825-0010", "쿠폰", 4_000L, memberCouponId, 0L);
        order.pay();
        order.ship(DeliveryCarrier.CJ, "123");
        order.deliver();
        order.recordEarnedPoint(160);
        order.requestReturn("ZZ-한 개만", Map.of(order.getItems().get(0).getId(), 1L));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        orderService.approveReturn(order.getId(), admin);

        // 재고는 **요청한 1개만**. 남은 수량(2개)이 아니다.
        verify(productCommandService).increaseStock(p1, 1, StockChangeReason.RETURN, order.getId());
        // 반품금액 10,000 − 쿠폰 몫(4000×10000/20000 = 2,000) = 환불 8,000
        // 등급 차감 8,000 · 적립 회수 160×8000/16000 = 80
        verify(pointService).refundReturnedOrder(memberId, 8_000L, 80L, 8_000L, order.getId());
        // 🔴 **쿠폰은 아직 이 주문에 걸려 있다** — 남은 1개가 그 할인을 쓰고 있다(G-10, G-4 와 같은 규칙).
        verify(couponService, never()).restore(any());
        // ⚠ 다시 요청할 수 있어야 하므로 배송완료로 되돌아간다.
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getItems().get(0).remainingQuantity()).isEqualTo(1);

        // 판매량도 **1개만** 되돌린다.
        OrderReturnedEvent event = capturePublished(OrderReturnedEvent.class);
        assertThat(event.refundedPoint()).isEqualTo(8_000L);
        assertThat(event.lines()).singleElement()
                .satisfies(l -> assertThat(l.quantity()).isEqualTo(1));
    }

    @Test
    @DisplayName("🔴 마지막 남은 것까지 반품하면 RETURNED 로 떨어지고 **그때 쿠폰을 복구한다**")
    void approveReturn_lastItemRestoresCoupon() {
        UUID p1 = UUID.randomUUID();
        UUID memberCouponId = UUID.randomUUID();
        Order order = Order.create(memberId, "구매자닉",
                List.of(OrderItem.of(p1, p1, null, "지바", null, 10_000, 10_000L, null, 2)),
                "수령인", "010-1234-5678", "06134", "서울시 강남구 테헤란로 1", "3층", null,
                0, "20260825-0011", "쿠폰", 4_000L, memberCouponId, 0L);
        order.pay();
        order.ship(DeliveryCarrier.CJ, "123");
        order.deliver();
        order.requestReturn("ZZ-전부", Map.of(order.getItems().get(0).getId(), 2L));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        orderService.approveReturn(order.getId(), admin);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.RETURNED);
        verify(couponService).restore(memberCouponId);
    }

    @Test
    @DisplayName("⚠ 반품 요청 검증: 품목을 하나도 안 고르면 거부한다 (빈 요청을 만들지 않는다)")
    void requestReturn_requiresItems() {
        Order order = deliveredOrder(UUID.randomUUID(), 10_000, 2, 0);
        when(orderRepository.findByIdAndMemberId(orderId, memberId)).thenReturn(Optional.of(order));

        assertErrorCode(() -> orderService.requestReturn(orderId, memberId, "ZZ-사유", Map.of()),
                ErrorCode.ORDER_RETURN_ITEM_REQUIRED);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("🔴 반품 요청 검증: 남은 수량을 넘기면 거부한다 — **취소와 코드가 갈린다**")
    void requestReturn_rejectsTooMuch() {
        Order order = deliveredOrder(UUID.randomUUID(), 10_000, 2, 0);
        UUID itemId = order.getItems().get(0).getId();
        when(orderRepository.findByIdAndMemberId(orderId, memberId)).thenReturn(Optional.of(order));

        // ⚠ 문구가 «취소 수량» 이면 고객이 «취소한 적 없는데?» 를 본다 — 그래서 코드를 나눴다.
        assertErrorCode(() -> orderService.requestReturn(orderId, memberId, "ZZ-사유", Map.of(itemId, 3L)),
                ErrorCode.ORDER_RETURN_QUANTITY_INVALID);
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ─────── 🔴 판매량 되돌림 — G-10 착수 중 찾은 G-4 구멍 (2026-08-25) ───────

    /**
     * 🔴 <b>부분 취소도 판매량을 되돌린다.</b>
     *
     * <p>⚠ 이 줄이 없으면 «부분 취소하고 그대로 두는» 정상 경로에서 {@code product.sold_count} 가
     * 안 줄어 <b>인기순이 틀어진다</b>. 부분 취소는 {@code OrderCancelledEvent} 를 <b>전량이 빠질
     * 때만</b> 내기 때문이다.
     *
     * <p>🔴 <b>발견 시점에 어긋난 값은 없었다</b> — 08-24 검증의 세 주문이 전부 {@code CANCELLED} 로
     * 끝나 «+전량 / −전량» 이 상쇄됐다. <b>즉 우연히 맞았다</b>(08-24 §5-4 매출 집계와 같은 모양).
     */
    @Test
    @DisplayName("🔴 부분 취소: 판매량을 **취소한 수량만큼만** 되돌린다 (G-4 가 두고 간 자리)")
    void cancelItem_reversesSalesByCancelledQuantityOnly() {
        UUID p1 = UUID.randomUUID();
        Order order = orderWith(OrderItem.of(p1, p1, null, "지바", null, 10_000, 10_000L, null, 3));
        order.pay();
        when(orderRepository.findByIdAndMemberId(order.getId(), memberId)).thenReturn(Optional.of(order));

        orderService.cancelItem(order.getId(), memberId, order.getItems().get(0).getId(), 1);

        OrderItemCancelledEvent event = capturePublished(OrderItemCancelledEvent.class);
        assertThat(event.lines()).singleElement().satisfies(l -> {
            assertThat(l.productId()).isEqualTo(p1);
            assertThat(l.quantity()).isEqualTo(1);   // 3 이 아니다
        });
        // ⚠ **«주문이 취소되었습니다» 알림은 안 나간다** — 1개만 뺐는데 그렇게 말하면 거짓이다.
        verify(eventPublisher, never()).publishEvent(any(OrderCancelledEvent.class));
    }

    /**
     * 🔴 <b>부분 취소 뒤 전체 취소 — 판매량이 «두 번» 줄면 안 된다.</b>
     *
     * <p>주문 시점에 +3 이 올라갔고 부분 취소가 −1 을 했으므로, 전체 취소는 <b>남은 2</b> 만 빼야
     * 정확히 상쇄된다. 원본 3 을 빼면 합계 −4 로 <b>안 판 것보다 더 줄어든다.</b>
     * ⚠ 2026-08-24 의 재고·적립금 사고와 <b>같은 모양</b>이다 — 원본을 읽느냐 남은 것을 읽느냐.
     */
    @Test
    @DisplayName("🔴 부분 취소 뒤 전체 취소: 판매량은 **남은 수량**만 되돌린다 (+3 −1 −2 = 0)")
    void cancelAfterPartial_reversesRemainingOnly() {
        UUID p1 = UUID.randomUUID();
        Order order = orderWith(OrderItem.of(p1, p1, null, "지바", null, 10_000, 10_000L, null, 3));
        order.pay();
        when(orderRepository.findByIdAndMemberId(order.getId(), memberId)).thenReturn(Optional.of(order));
        orderService.cancelItem(order.getId(), memberId, order.getItems().get(0).getId(), 1);

        orderService.cancel(order.getId(), memberId, null);

        OrderCancelledEvent cancelled = capturePublished(OrderCancelledEvent.class);
        assertThat(cancelled.lines()).singleElement()
                .satisfies(l -> assertThat(l.quantity()).isEqualTo(2));
    }
}
