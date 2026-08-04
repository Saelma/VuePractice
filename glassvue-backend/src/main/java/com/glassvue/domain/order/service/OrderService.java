package com.glassvue.domain.order.service;

import com.glassvue.domain.cart.dto.CartItemResponse;
import com.glassvue.domain.cart.dto.CartResponse;
import com.glassvue.domain.cart.service.CartService;
import com.glassvue.domain.catalog.entity.StockChangeReason;
import com.glassvue.domain.catalog.service.command.ProductCommandService;
import com.glassvue.domain.coupon.service.CouponService;
import com.glassvue.domain.point.service.PointService;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.service.MemberService;
import com.glassvue.domain.order.dto.AdminOrderResponse;
import com.glassvue.domain.order.dto.OrderCreateRequest;
import com.glassvue.domain.order.dto.OrderResponse;
import com.glassvue.domain.order.dto.OrderSearchCondition;
import com.glassvue.domain.order.config.DeliveryProperties;
import com.glassvue.domain.order.entity.DeliveryCarrier;
import com.glassvue.domain.order.entity.Order;
import com.glassvue.domain.order.entity.OrderItem;
import com.glassvue.domain.order.entity.OrderStatus;
import com.glassvue.domain.order.event.OrderCancelledEvent;
import com.glassvue.domain.order.event.OrderDeliveredEvent;
import com.glassvue.domain.order.event.OrderPlacedEvent;
import com.glassvue.domain.order.event.OrderReturnedEvent;
import com.glassvue.domain.order.repository.OrderRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.policy.ShippingPolicy;
import com.glassvue.global.response.PageResponse;
import com.glassvue.global.security.AuthUser;
import org.springframework.context.ApplicationEventPublisher;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final ProductCommandService productCommandService;
    private final ApplicationEventPublisher eventPublisher;
    private final DeliveryProperties deliveryProperties;
    private final ShippingPolicy shippingPolicy;
    private final CouponService couponService;
    private final PointService pointService;
    private final MemberService memberService; // 정지 회원 주문 차단(B-11 후속) — member 공개 API

    /**
     * 장바구니 → 주문 생성. 재고 원자적 차감 + 카트 비우기.
     *
     * <p>{@code AuthUser}를 받는 이유는 닉네임을 주문에 스냅샷으로 남기기 위해서다
     * (탈퇴하면 회원 row가 사라지므로 조회 방식으로는 과거 주문의 구매자를 알 수 없다).
     */
    @Transactional
    public UUID checkout(AuthUser user, OrderCreateRequest req) {
        UUID memberId = user.id();
        // 정지 회원은 주문 불가(B-11 후속, 전면 차단). 로그인·갱신도 막지만, access 만료 전(≤30분)
        // 남은 토큰으로 주문하는 창까지 여기서 닫는다.
        if (memberService.isSuspended(memberId)) {
            throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
        }
        CartResponse cart = cartService.getCart(memberId);
        if (cart.items().isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }
        if (cart.items().stream().anyMatch(i -> !i.available())) {
            throw new BusinessException(ErrorCode.UNAVAILABLE_ITEM);
        }

        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItemResponse i : cart.items()) {
            // 옵션 id·이름도 스냅샷한다 — 취소 시 재고 복원 대상이고, 옵션명은 주문 내역 표시용이다
            // (정가·배송지·운송장과 같은 스냅샷 원칙).
            orderItems.add(OrderItem.of(i.productId(), i.variantId(), i.optionName(),
                    i.name(), i.thumbUrl(), i.price(), i.listPrice(), i.quantity()));
        }

        // 금액 계산 순서: 상품합계 → 쿠폰할인 → 배송비 → 결제금액.
        //
        // ⚠ 배송비는 **할인 전** 상품합계로 정한다(2026-07-23 결정) — 쿠폰을 썼다고 배송비가 붙으면
        // 고객이 손해 본 기분이 든다. 덕분에 이 줄은 쿠폰이 생겨도 그대로다.
        long shippingFee = shippingPolicy.feeFor(cart.totalPrice());

        // 쿠폰은 coupon 도메인의 공개 API로만 다룬다(엔티티·리포지토리를 직접 만지지 않는다).
        // 검증과 사용처리가 redeem 한 번에 끝나므로 "검증했으니 이제 써도 되겠지" 사이의 틈이 없다.
        // 같은 트랜잭션이라 주문이 롤백되면 쿠폰 사용도 함께 롤백된다.
        String couponName = null;
        long couponDiscount = 0L;
        if (req.memberCouponId() != null) {
            couponName = couponService.nameOf(req.memberCouponId());
            couponDiscount = couponService.redeem(req.memberCouponId(), memberId, cart.totalPrice());
        }

        // 적립금 사용. 쿠폰 다음, 배송비 앞이다:
        //     상품합계 → 쿠폰할인 → **적립금** → 배송비 → 결제금액
        // 상한을 **상품합계 − 쿠폰할인**으로 넘긴다 — 넘으면 결제금액이 음수가 되거나
        // 배송비를 적립금으로 내는 이상한 상태가 된다(쿠폰 할인의 상한과 같은 판단).
        // 쿠폰과 마찬가지로 use()가 검증과 차감을 한 번에 하고, 주문이 롤백되면 함께 롤백된다.
        long usedPoint = (req.usePoint() == null || req.usePoint() <= 0) ? 0L : req.usePoint();

        // 배송비·할인액 모두 **서버가 계산해 스냅샷**한다 — 요청 본문으로 받으면 위조할 수 있다
        // (품목·가격을 장바구니에서 읽는 것과 같은 이유).
        Order order = orderRepository.save(Order.create(memberId, user.nickname(), orderItems,
                req.recipient(), req.phone(), req.zipcode(), req.address1(), req.address2(),
                req.shipMemo(),
                shippingFee, nextOrderNo(), couponName, couponDiscount, usedPoint));

        // 재고 차감도 **주문을 만든 뒤**로 옮겼다(2026-08-04, B-19) — 아래 적립금과 **같은 이유**다.
        // 재고 이력이 "어느 주문 때문인지"를 담아야 하는데, 주문보다 먼저 차감하면 order_id 가 없어
        // 근거 없는 행이 된다. 같은 트랜잭션이라 순서를 바꿔도 원자성은 그대로고, 재고가 부족하면
        // OUT_OF_STOCK 으로 주문째 롤백된다(2026-07-24 C-8 이후 옵션 단위).
        //
        // ⚠ 대가: 롤백돼도 **주문번호 시퀀스는 되돌아가지 않아** 번호에 구멍이 난다(V15 는 NOCACHE 로
        //    구멍을 피하려 했다). 다만 이건 **이미 있던 성질**이다 — 적립금 부족으로 실패하는 경로가
        //    원래 save() 뒤에 있었다. 여기서 늘어난 건 "동시에 마지막 재고를 집는 경합" 하나뿐이고,
        //    장바구니 단계에서 available() 로 이미 걸러지므로 실제로 닿기 어렵다.
        for (CartItemResponse i : cart.items()) {
            productCommandService.decreaseStock(i.variantId(), i.quantity(), order.getId());
        }

        // 차감은 **주문을 만든 뒤**에 한다 — 적립금 이력이 "어느 주문 때문인지"를 담아야 하는데
        // 주문보다 먼저 차감하면 order_id 를 넣을 수 없다(이력만 있고 근거가 없는 행이 된다).
        // 같은 트랜잭션이라 순서를 바꿔도 원자성은 그대로고, 실패하면 주문째 롤백된다.
        if (usedPoint > 0) {
            pointService.use(memberId, usedPoint, cart.totalPrice() - couponDiscount, order.getId());
        }
        cartService.clear(memberId);
        // 도메인 이벤트 발행 — 구독자(알림·포인트 등)는 order가 모른다. AFTER_COMMIT 리스너가 커밋된 주문에만 반응.
        eventPublisher.publishEvent(OrderPlacedEvent.from(order));
        log.info("Order created: {} by {}", order.getId(), memberId);
        return order.getId();
    }

    /** 회원이 해당 상품을 구매(취소되지 않은 주문)했는지 — 리뷰 도메인 등 타 도메인 공개 API. */
    @Transactional(readOnly = true)
    public boolean hasPurchased(UUID memberId, UUID productId) {
        return orderRepository.existsPurchase(memberId, productId);
    }

    /**
     * 내 주문 목록(페이징). 조건의 소유자 스코프를 서버가 본인 id로 고정하므로
     * 클라이언트가 memberId를 넘겨 남의 주문을 볼 수 없다.
     */
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> myOrders(UUID memberId, OrderSearchCondition condition, Pageable pageable) {
        Page<Order> page = orderRepository.search(condition.scopedTo(memberId), pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    /** 관리자 주문 목록 — 전체 주문. 구매자 정보를 포함한 별도 응답을 쓴다. */
    @Transactional(readOnly = true)
    public PageResponse<AdminOrderResponse> adminOrders(OrderSearchCondition condition, Pageable pageable) {
        Page<Order> page = orderRepository.search(condition.forAll(), pageable);
        return PageResponse.from(page.map(AdminOrderResponse::from));
    }

    /**
     * 특정 회원의 주문 목록(관리자, B-11 회원 상세). {@code scopedTo} 가 memberId 로 범위를 좁히고
     * status 필터는 유지하므로, status=RETURN_REQUESTED/RETURNED 로 그 회원의 <b>반품 이력</b>도 본다.
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminOrderResponse> adminOrdersOf(
            UUID memberId, OrderSearchCondition condition, Pageable pageable) {
        Page<Order> page = orderRepository.search(condition.scopedTo(memberId), pageable);
        return PageResponse.from(page.map(AdminOrderResponse::from));
    }

    /**
     * 관리자용 상태별 주문 건수. 화면 상단 탭에 "발송 대기 N건"처럼 띄워
     * 필터를 바꿔보지 않고도 할 일이 얼마나 남았는지 알 수 있게 한다.
     * 건수가 0인 상태도 키는 항상 포함한다(탭이 사라지면 오히려 헷갈린다).
     */
    @Transactional(readOnly = true)
    public Map<OrderStatus, Long> adminOrderCounts() {
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (OrderStatus s : OrderStatus.values()) {
            counts.put(s, 0L);
        }
        for (Object[] row : orderRepository.countByStatus()) {
            counts.put((OrderStatus) row[0], (Long) row[1]);
        }
        return counts;
    }

    /** 주문 상세 — 본인 주문만. 단, ADMIN은 발송 처리를 위해 전체 주문 조회 가능. */
    @Transactional(readOnly = true)
    public OrderResponse get(UUID id, AuthUser user) {
        Order order = (user.role() == Role.ADMIN
                ? orderRepository.findById(id)
                : orderRepository.findByIdAndMemberId(id, user.id()))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        return toResponse(order);
    }

    /**
     * 사람이 읽는 주문번호 — {@code yyyyMMdd}(Asia/Seoul) + 전역 일련번호(V15의 시퀀스).
     *
     * <p>시퀀스라 동시 주문에서 같은 번호를 잡는 일이 <b>원천적으로 불가능</b>하다(재시도 로직 불필요).
     * 날짜를 Asia/Seoul 로 뽑는 이유: {@code created_at} 은 UTC 로 저장돼서, 서버 기본 시간대를 쓰면
     * 한국 시간 00:00~09:00 주문의 날짜가 하루 밀린다. 마이그레이션의 백필도 같은 기준을 쓴다.
     */
    private String nextOrderNo() {
        String date = DateTimeFormatter.ofPattern("yyyyMMdd")
                .format(java.time.LocalDate.now(ZoneId.of("Asia/Seoul")));
        return date + "-" + String.format("%04d", orderRepository.nextOrderNoSequence());
    }

    /** 조회 링크는 설정으로 만들어 응답에 실어 준다 — 화면이 택배사별 URL 형식을 알 필요가 없게. */
    private OrderResponse toResponse(Order order) {
        return OrderResponse.from(order,
                deliveryProperties.resolve(order.getShipCarrier(), order.getShipTrackingNo()));
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

    /**
     * 발송 처리(관리자 전용, 권한은 SecurityConfig에서 강제) — PAID 상태만.
     *
     * <p>운송장(택배사·송장번호)을 함께 받는다. 발송과 운송장 등록을 한 트랜잭션으로 묶어야
     * "발송됐는데 추적 정보가 없는" 중간 상태가 생기지 않는다.
     */
    @Transactional
    public void ship(UUID id, DeliveryCarrier carrier, String trackingNo) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isShippable()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_SHIPPABLE);
        }
        order.ship(carrier, trackingNo);
        log.info("Order shipped: {} via {} ({})", id, carrier, trackingNo);
    }

    /**
     * 배송완료 처리(관리자 전용) — SHIPPED 상태만.
     *
     * <p>실제 커머스는 택배사 웹훅으로 자동 전이하지만, 지금은 관리자가 누르는 수동 전이다
     * (PG 연동과 같은 자리 — 나중에 자동화하더라도 이 상태 전이 자체는 그대로 쓰인다).
     */
    @Transactional
    public void deliver(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isDeliverable()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_DELIVERABLE);
        }
        order.deliver();

        // ⚠ 적립은 **동기**다. 이 프로젝트의 리스너는 @Async(인프로세스 best-effort)라 유실될 수 있는데,
        //    알림이 유실되면 메일 한 통을 놓치지만 **적립이 유실되면 고객 돈이 사라진다.**
        //    재고 복원을 이벤트로 빼지 않은 것과 같은 판단이다(ARCHITECTURE).
        //    같은 트랜잭션이므로 배송완료가 롤백되면 적립도 함께 롤백된다.
        long earned = pointService.earnOnDelivery(order.getMemberId(), order.rewardableAmount(), id);
        order.recordEarnedPoint(earned);

        // 이벤트는 **알림용**이다 — 적립을 시키는 게 아니라 결과를 알린다.
        eventPublisher.publishEvent(OrderDeliveredEvent.from(order, earned));
        log.info("Order delivered: {} earned={}", id, earned);
    }

    /**
     * 주문 취소 — 본인 주문·취소가능(ORDERED/PAID) 상태만. 재고 복원.
     *
     * <p>2026-08-04(B-17): {@code reason} 을 받는다 — <b>선택</b>이다(null 가능).
     * 반품엔 사유가 있는데 취소엔 없어 관리자가 "왜 취소됐는지" 를 알 수 없던 비대칭을 없앤다.
     * 재고 이력의 {@code CANCEL} 줄은 {@code order_id} 를 갖고 있으므로 여기 사유가 있으면
     * 되짚어진다 — <b>이력에 사유를 복사하지 않는다</b>(같은 정보를 두 번 적지 않는다, V39 와 같은 규칙).
     */
    @Transactional
    public void cancel(UUID id, UUID memberId, String reason) {
        Order order = orderRepository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isCancellable()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CANCELLABLE);
        }
        order.cancel(reason);
        order.getItems().forEach(it -> productCommandService.increaseStock(
                it.getVariantId(), it.getQuantity(), StockChangeReason.CANCEL, id));
        eventPublisher.publishEvent(OrderCancelledEvent.from(order));
        log.info("Order cancelled: {}", id);
    }

    /**
     * 반품 요청 (2026-07-24, C-9) — 배송완료 주문만. 본인 주문만(findByIdAndMemberId).
     * 아직 환불·재고 복원은 하지 않는다 — 관리자 승인 때 한다.
     */
    @Transactional
    public void requestReturn(UUID id, UUID memberId, String reason) {
        Order order = orderRepository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isReturnRequestable()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_RETURNABLE);
        }
        order.requestReturn(reason);
        log.info("Return requested: {} by {}", id, memberId);
    }

    /**
     * 반품 승인(관리자) — 옵션 재고 복원 + 적립금 환불. 요청된 반품만 승인할 수 있다.
     *
     * <p>재고 복원은 취소와 같고, 환불은 point 도메인 공개 API 로만 한다(도메인 경계).
     * 한 트랜잭션이라 재고·환불·상태가 함께 커밋되거나 함께 롤백된다.
     */
    @Transactional
    public void approveReturn(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isReturnPending()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_RETURN_PENDING);
        }
        order.approveReturn();
        // 물건이 돌아왔으니 재고 복원(취소와 동일 — 옵션 단위).
        // 재고 이력에서는 취소와 구분한다(B-19) — 원장에서 "왜 돌아왔는지"가 구분돼야 값이 있다.
        order.getItems().forEach(it -> productCommandService.increaseStock(
                it.getVariantId(), it.getQuantity(), StockChangeReason.RETURN, id));
        // 환불 = 상품합계−쿠폰을 적립금으로, 배송완료 적립은 회수, 등급 기준에서도 차감.
        pointService.refundReturnedOrder(order.getMemberId(),
                order.refundableAmount(), order.getEarnedPoint(), order.rewardableAmount(), id);
        // 판매량 되돌림은 catalog 가 구독한다 — 환불(동기)이 끝난 뒤 결과 알림(주문 취소와 같은 규약).
        eventPublisher.publishEvent(OrderReturnedEvent.from(order));
        log.info("Return approved: {}", id);
    }

    /** 반품 거절(관리자) — 배송완료로 되돌린다. 재고·적립은 건드리지 않는다(승인 안 했으니). */
    @Transactional
    public void rejectReturn(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isReturnPending()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_RETURN_PENDING);
        }
        order.rejectReturn();
        log.info("Return rejected: {}", id);
    }
}
