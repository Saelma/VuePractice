package com.glassvue.domain.order.service;

import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.domain.audit.event.AdminActionEvent;
import com.glassvue.domain.cart.dto.CartItemResponse;
import com.glassvue.domain.cart.dto.CartResponse;
import com.glassvue.domain.cart.service.CartService;
import com.glassvue.domain.catalog.entity.StockChangeReason;
import com.glassvue.domain.catalog.service.command.ProductCommandService;
import com.glassvue.domain.coupon.service.CouponService;
import com.glassvue.domain.point.service.PointService;
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
import com.glassvue.domain.order.entity.ReturnSettlement;
import com.glassvue.domain.order.event.OrderCancelledEvent;
import com.glassvue.domain.order.event.OrderDeliveredEvent;
import com.glassvue.domain.order.event.OrderItemCancelledEvent;
import com.glassvue.domain.order.event.OrderPlacedEvent;
import com.glassvue.domain.order.event.OrderReturnRejectedEvent;
import com.glassvue.domain.order.event.OrderReturnRequestedEvent;
import com.glassvue.domain.order.event.OrderReturnedEvent;
import com.glassvue.domain.order.event.SoldLine;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
            // ⚠ regularPrice 는 **세일 전 판매가**다(G-9, 2026-08-20) — 정가(listPrice)와 다른 값이고
            //    둘 다 스냅샷한다. 그전에는 이 값을 나를 통로가 아예 없어서
            //    «원래 얼마였는데 세일로 얼마에 샀다» 가 주문에 안 남았다.
            orderItems.add(OrderItem.of(i.productId(), i.variantId(), i.optionName(),
                    i.name(), i.thumbUrl(), i.price(), i.regularPrice(), i.listPrice(), i.quantity()));
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
                shippingFee, nextOrderNo(),
                // ⚠ 쿠폰은 이름·금액(스냅샷)과 **id(되돌릴 대상)** 를 함께 남긴다 — id 가 없으면
                //   취소 때 «어느 장이었는지» 를 알 수 없어 복구가 불가능하다(V46, 2026-08-11).
                couponName, couponDiscount, req.memberCouponId(),
                usedPoint));

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

    /** 주문 상세 — 본인 주문만. 단, 관리자(SUPER_ADMIN 포함)는 발송 처리를 위해 전체 주문 조회 가능. */
    @Transactional(readOnly = true)
    public OrderResponse get(UUID id, AuthUser user) {
        Order order = (user.isAdmin()
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
    public void ship(UUID id, AuthUser actor, DeliveryCarrier carrier, String trackingNo) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isShippable()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_SHIPPABLE);
        }
        order.ship(carrier, trackingNo);
        publishAudit(AuditAction.ORDER_SHIP, actor, order,
                carrier.getDisplayName() + " " + trackingNo);
        log.info("Order shipped: {} via {} ({}) admin={}", id, carrier, trackingNo, actor.id());
    }

    /**
     * 배송완료 처리(관리자 전용) — SHIPPED 상태만.
     *
     * <p>실제 커머스는 택배사 웹훅으로 자동 전이하지만, 지금은 관리자가 누르는 수동 전이다
     * (PG 연동과 같은 자리 — 나중에 자동화하더라도 이 상태 전이 자체는 그대로 쓰인다).
     */
    @Transactional
    public void deliver(UUID id, AuthUser actor) {
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
        // ⚠ 원장에는 **나간 적립금**을 적는다 — 이 조작이 실제로 움직인 것이 그것이다(2026-08-14).
        publishAudit(AuditAction.ORDER_DELIVER, actor, order, "적립 " + earned + "P");
        log.info("Order delivered: {} earned={} admin={}", id, earned, actor.id());
    }

    /**
     * 주문 취소 — 본인 주문·취소가능(ORDERED/PAID) 상태만. 재고 복원.
     *
     * <p>2026-08-04(B-17): {@code reason} 을 받는다 — <b>선택</b>이다(null 가능).
     * 반품엔 사유가 있는데 취소엔 없어 관리자가 "왜 취소됐는지" 를 알 수 없던 비대칭을 없앤다.
     * 재고 이력의 {@code CANCEL} 줄은 {@code order_id} 를 갖고 있으므로 여기 사유가 있으면
     * 되짚어진다 — <b>이력에 사유를 복사하지 않는다</b>(같은 정보를 두 번 적지 않는다, V39 와 같은 규칙).
     *
     * <p>🔴 2026-08-07: <b>쓴 적립금을 돌려준다.</b> 그전까지는 재고만 복원하고 적립금은 그대로 뒀는데,
     * 차감은 <b>주문 시점</b>에 이미 끝나 있다({@code checkout} 의 {@code pointService.use}).
     * 즉 적립금을 쓰고 취소하면 <b>그 돈이 사라졌다</b> — 화면은 «취소됨» 으로 멀쩡하고 알림도 정상이라
     * 고객이 잔액을 들여다보기 전까지 아무도 모른다. 반품 승인은 처음부터 환불했으므로 <b>비대칭</b>이었다
     * (B-17 이 «반품엔 사유가 있는데 취소엔 없다» 를 고친 것과 같은 모양의 구멍이다).
     *
     * <p>⚠ <b>이벤트로 빼지 않는다.</b> {@code @Async} 는 best-effort 라 유실되면 고객 돈이 사라진다 —
     * 재고 복원을 이벤트로 빼지 않은 것, 배송완료 적립을 동기로 둔 것과 같은 판단이다.
     * 순서도 반품 승인과 맞춘다: <b>재고 → 환불 → 이벤트(알림)</b>.
     */
    @Transactional
    public void cancel(UUID id, UUID memberId, String reason) {
        Order order = orderRepository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        requireCancellable(order);
        order.cancel(reason);
        applyCancellation(order);
        log.info("Order cancelled: {} refundedPoint={}", id, order.getUsedPoint());
    }

    /**
     * 관리자 대행 취소 (2026-08-10, 백로그 B-25).
     *
     * <p>⚠ <b>본인 취소와 다른 것은 「누구의 주문을 찾는가」와 「누가 했는지 남기는가」 둘뿐</b>이다.
     * 재고 복원·적립금 환불·알림은 {@link #applyCancellation} 로 <b>같은 코드를 탄다</b> —
     * 갈라 두면 한쪽만 고쳐지고, 그 어긋남은 <b>돈에서 난다</b>(2026-08-07 에 취소가 적립금을 안 돌려주던
     * 것이 정확히 «반품만 고쳐진» 비대칭이었다, WA §1-2-1).
     *
     * <p>⚠ <b>허용 상태는 본인과 같다</b>({@code ORDERED}·{@code PAID}) — {@link Order#isCancellable} 을
     * 그대로 쓴다. 발송 이후는 물건이 나가 있어 <b>회수 절차</b>가 필요한데, 그 자리는 이미
     * 반품(요청 → 관리자 승인)이 맡고 있다. 여기서 발송 후를 열면 재고를 <b>돌아오지도 않은 물건</b>으로
     * 복원하게 된다(2026-08-10 결정).
     *
     * <p>⚠ 감사 기록은 {@code AdminActionEvent} 로만 낸다 — order 가 audit 을 직접 부르지 않는다
     * (도메인 간 직접 참조 금지). 같은 트랜잭션이라 <b>감사가 실패하면 취소도 롤백</b>된다.
     */
    @Transactional
    public void cancelByAdmin(UUID id, AuthUser actor, String reason) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        requireCancellable(order);
        order.cancelByAdmin(reason, actor.id(), actor.nickname());
        applyCancellation(order);
        publishAudit(AuditAction.ORDER_CANCEL, actor, order, reason);
        log.info("Order cancelled by admin: {} admin={} refundedPoint={}",
                id, actor.id(), order.getUsedPoint());
    }

    /**
     * 🔴 <b>부분 취소 — 본인 주문</b> (2026-08-24, BACKLOG G-4).
     *
     * <p>정산 규칙의 원본은 BACKLOG G-4 「결정 (2026-08-24, 사용자 확정)」이고, 배분식 자체는
     * {@link Order#cancelItem} 에 있다. 여기서 하는 일은 <b>되돌리는 것들을 순서대로 부르는 것</b>이다.
     *
     * <p>⚠ <b>허용 상태는 전체 취소와 같다</b>({@code ORDERED}·{@code PAID}). 발송 이후는 물건이 나가
     * 있어 회수 절차가 필요하고, 그 자리는 반품이 맡는다({@code cancelByAdmin} 주석과 같은 판단).
     */
    @Transactional
    public void cancelItem(UUID orderId, UUID memberId, UUID orderItemId, long quantity) {
        Order order = orderRepository.findByIdAndMemberId(orderId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        // actor 가 null 이면 «본인» 이다 — Order.cancelledBy 의 NULL 규약과 같은 뜻이다.
        applyItemCancellation(order, orderItemId, quantity, null);
    }

    /**
     * 🔴 <b>부분 취소 — 관리자 대행</b> (2026-08-24, BACKLOG G-4).
     *
     * <p>⚠ <b>본인 취소와 다른 것은 「누구의 주문을 찾는가」와 「원장에 남기는가」 둘뿐이다</b> —
     * {@link #cancel} 과 {@link #cancelByAdmin} 이 {@link #applyCancellation} 을 함께 타는 것과
     * 같은 모양이다. 정산은 {@link #applyItemCancellation} 하나만 탄다.
     */
    @Transactional
    public void cancelItemByAdmin(UUID orderId, AuthUser actor, UUID orderItemId, long quantity) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        OrderItem item = findItem(order, orderItemId);
        String itemName = item.getProductName()
                + (item.getVariantName() == null ? "" : " (" + item.getVariantName() + ")");

        long refund = applyItemCancellation(order, orderItemId, quantity, actor);

        // ⚠ 「무엇을 몇 개 빼고 얼마를 돌려줬나」 — AuditAction.ORDER_ITEM_CANCEL 주석이 정한 내용이다.
        // 🔴 **주문이 이 회차로 죽었으면 그것도 적는다**(2026-08-26, I-6). 원장만 보는 사람은
        //    «품목 하나 뺐다» 와 «그 하나가 마지막이라 주문이 끝났다» 를 구분할 방법이 없다.
        //    ⚠ 앞부분(품목·수량)은 고객 알림과 **같은 문자열**을 유지한다 — 뒤에만 덧붙인다.
        publishAudit(AuditAction.ORDER_ITEM_CANCEL, actor, order,
                itemName + " " + quantity + "개 취소 / 환불 " + refund + "원"
                        + (order.getStatus() == OrderStatus.CANCELLED ? " (마지막 품목 — 주문 취소됨)" : ""));
    }

    /**
     * 🔴 <b>부분 취소가 확정된 뒤 따라와야 하는 일 전부</b> — 정산 → 재고 → 적립금 → (전량이면 주문 취소).
     *
     * <p>⚠ <b>{@link #applyCancellation} 과 같은 규약을 따른다</b>: 되돌리는 것들은 전부 앞에 오고
     * 이벤트만 뒤에 온다. 그 메서드 주석이 못박은 것 — *"«주문에 붙는 것»(할인·혜택·차감)을 새로
     * 만들면 여기에 되돌리는 줄이 있는지 먼저 본다"* — 이 그대로 적용된다.
     *
     * <p>🔴 <b>여기서 되돌리는 것과 안 되돌리는 것을 갈라 둔다</b>(G-4 결정):
     * <ul>
     *   <li><b>재고</b> — 되돌린다. 취소한 수량만큼만.</li>
     *   <li><b>적립금(사용분)</b> — 되돌린다. 배분된 몫만큼 계정으로.</li>
     *   <li><b>쿠폰</b> — 🔴 <b>안 되돌린다.</b> 쿠폰은 여전히 이 주문에 걸려 있다(결정 1).
     *       마지막 품목까지 빠져 주문이 통째로 취소될 때 {@link #applyCancellation} 이 복구한다.</li>
     *   <li><b>배송비</b> — 안 건드린다(결정 2). 부분 취소로 움직일 수 없는 값이다.</li>
     *   <li><b>적립(earn)</b> — 회수 대상이 아니다. 적립은 {@code deliver()} 에서 일어나는데
     *       부분 취소는 {@code ORDERED}·{@code PAID} 에서만 되므로 <b>아직 적립이 없다</b>.</li>
     * </ul>
     *
     * <p>🔴 <b>마지막 품목이 빠지면 주문을 {@code CANCELLED} 로 떨어뜨린다.</b> 「품목이 다 빠졌는데
     * 상태는 {@code PAID}」인 주문을 만들지 않는다 — 그런 주문은 매출에도 잡히고 발송 대기로도 보인다.
     * ⚠ 그때 {@link #applyCancellation} 을 부르지 <b>않는다</b>: 재고·적립금은 이미 품목별로 되돌렸으니
     * 또 부르면 <b>두 번 돌려준다</b>. 대신 아직 안 한 것 둘만 한다 — <b>쿠폰 복구</b>와 <b>알림</b>.
     *
     * @return 이 회차에 돌려준 돈
     */
    private long applyItemCancellation(Order order, UUID orderItemId, long quantity, AuthUser actor) {
        requireCancellable(order);
        OrderItem item = findItem(order, orderItemId);
        if (quantity <= 0 || quantity > item.remainingQuantity()) {
            throw new BusinessException(ErrorCode.ORDER_ITEM_QUANTITY_INVALID);
        }
        if (order.remainingItemsTotal() <= 0) {
            throw new BusinessException(ErrorCode.ORDER_NO_REMAINING_ITEM);
        }

        long pointBefore = order.getCancelledPoint();
        // 🔴 **이번 회차에 빠진 «상품합계»** (2026-08-26, I-12). 주문이 이 회차로 비면 고객 알림이
        //    이 값을 말한다 — 그때 `remainingItemsTotal()` 은 이미 0 이라 못 쓴다(0원이라고 말했다).
        long itemsBefore = order.getCancelledItemsTotal();
        long refund = order.cancelItem(item, quantity);
        long refundedPoint = order.getCancelledPoint() - pointBefore;
        long cancelledItemsAmount = order.getCancelledItemsTotal() - itemsBefore;

        productCommandService.increaseStock(
                item.getVariantId(), quantity, StockChangeReason.CANCEL, order.getId());
        if (refundedPoint > 0) {
            pointService.refundCancelledOrder(order.getMemberId(), refundedPoint, order.getId());
        }
        // 🔴 **판매량도 되돌린다** (2026-08-25, G-10 착수 중 발견한 G-4 구멍). 이 줄이 없으면
        //    «부분 취소하고 그대로 두는» 정상 경로에서 `product.sold_count` 가 안 줄어 인기순이 틀어진다.
        //    ⚠ 전량이 빠져 아래 OrderCancelledEvent 가 나가는 경우에도 **겹치지 않는다** —
        //       그쪽은 «남은 수량» 을 싣는데 여기서 이미 뺐으므로 그때 남은 것은 0 이다.
        // ⚠ 「무엇을 몇 개」 는 감사 원장이 쓰는 것과 **같은 문자열**이다(cancelItemByAdmin 참고) —
        //    두 곳이 다른 말을 하면 CS 에서 고객 알림과 원장을 못 맞춘다.
        String itemsSummary = item.getProductName()
                + (item.getVariantName() == null ? "" : " (" + item.getVariantName() + ")")
                + " " + quantity + "개";
        eventPublisher.publishEvent(new OrderItemCancelledEvent(
                order.getId(), order.getMemberId(), order.getOrderNo(), SoldLine.of(item, quantity),
                itemsSummary, refund, refundedPoint, order.isFullyCancelledByItems()));

        if (order.isFullyCancelledByItems()) {
            // 🔴 남은 게 없으면 주문 자체가 취소다. 재고·적립금은 위에서 이미 되돌렸다 —
            //    여기서 applyCancellation 을 부르면 **두 번** 돌려준다.
            // 🔴 **누가 비웠는지를 남긴다**(2026-08-26, BACKLOG I-6). 예전엔 여기가 언제나
            //    `order.cancel(null)` 이었고, NULL 은 이 저장소에서 «본인이 취소했다» 는 뜻이라
            //    **관리자가 대행한 주문이 주문 행에서 거짓말을 했다**(고객 상세의 「고객센터에서
            //    대신 취소했어요」 줄도 안 떴다). ⚠ 마지막 품목을 뺀 쪽이 곧 «주문을 취소한 쪽» 이다 —
            //    앞 회차를 누가 했든 **이 회차의 행위자**로 적는다(그 회차들은 원장에 각각 남아 있다).
            if (actor == null) {
                order.cancel(null);
            } else {
                order.cancelByAdminFromItems(actor.id(), actor.nickname());
            }
            couponService.restore(order.getMemberCouponId());
            // ⚠ `from(order)` 이 아니다 — 그건 «남은 상품합계» 를 읽는데 여기선 이미 0 이다(I-12).
            eventPublisher.publishEvent(OrderCancelledEvent.ofItemsDrained(order, cancelledItemsAmount));
            log.info("Order fully cancelled by item cancellations: {}", order.getOrderNo());
        }
        log.info("Order item cancelled: {} item={} qty={} refund={} refundedPoint={}",
                order.getOrderNo(), orderItemId, quantity, refund, refundedPoint);
        return refund;
    }

    private OrderItem findItem(Order order, UUID orderItemId) {
        return order.getItems().stream()
                .filter(i -> i.getId().equals(orderItemId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND));
    }

    /**
     * 관리자 주문 조작을 원장에 남긴다 (2026-08-14, V51 — 취소 하나였던 자리를 다섯으로 넓히며 뽑았다).
     *
     * <p>⚠ <b>대상은 «주문» 이 아니라 «주문자» 다</b> — 감사 테이블의 target 은 회원이라 모양이 맞는다.
     * 주문 자체는 {@code detail} 에 주문번호로 남긴다(B-18 에서 리뷰에 감사를 못 붙인 이유가 여기엔 없다).
     *
     * <p>⚠ <b>order 가 audit 을 직접 부르지 않는다</b> — 이벤트로만 낸다(도메인 간 직접 참조 금지).
     * 같은 트랜잭션이라 <b>감사가 실패하면 조작도 롤백</b>된다.
     *
     * <p>⚠ {@code what} 은 «이 조작이 무엇을 움직였는가» 다 — 택배사·송장 · 나간 적립금 · 환불액 · 사유.
     * 주문번호는 여기서 붙이므로 <b>호출자가 또 적지 않는다</b>(V43 때는 호출자가 직접 이어 붙였다).
     */
    /**
     * 원장 {@code detail} 의 상한 — {@code AdminAuditLog.detail} 이 {@code VARCHAR2(1000)} 이다.
     *
     * <p>🔴 <b>넘으면 조작 자체가 롤백된다.</b> 감사는 <b>같은 트랜잭션</b>에서 저장되므로
     * (`AdminAuditCommandService` — «감사가 실패하면 조작 전체가 함께 롤백된다»),
     * 길이 초과({@code ORA-12899})는 «원장만 못 남는 것» 이 아니라 <b>반품 승인·취소가 실패하는 것</b>이다.
     * ⚠ <b>저장소 어디에도 자르는 곳이 없었다</b>(2026-08-26 확인) — 지금까지는 문자열이 짧아서
     * 안 터진 것이지 막혀 있던 것이 아니다.
     */
    private static final int AUDIT_DETAIL_MAX = 1000;

    private void publishAudit(AuditAction action, AuthUser actor, Order order, String what) {
        eventPublisher.publishEvent(new AdminActionEvent(
                action, actor.id(), actor.nickname(),
                order.getMemberId(), memberService.loginIdOf(order.getMemberId()),
                fitDetail(order.getOrderNo() + " / " + what)));
    }

    /**
     * 원장 detail 을 상한 안으로 눕힌다 — <b>자르되 «잘렸다» 고 말한다.</b>
     *
     * <p>⚠ 조용히 자르면 읽는 사람이 <b>그게 전부인 줄</b> 안다. 특히 사유가 뒤에 붙으므로
     * 잘리는 것은 대개 <b>사유의 꼬리</b>다({@code AuditAction.ORDER_RETURN_APPROVE} 주석 참조).
     * 🔴 <b>품목이 아주 많은 주문은 사유가 통째로 날아갈 수도 있다</b> — 그건 detail 한 칸으로
     * 풀 문제가 아니라서 BACKLOG §I-13 에 따로 적었다.
     */
    private static String fitDetail(String detail) {
        if (detail.length() <= AUDIT_DETAIL_MAX) {
            return detail;
        }
        String mark = "…(잘림)";
        return detail.substring(0, AUDIT_DETAIL_MAX - mark.length()) + mark;
    }

    private void requireCancellable(Order order) {
        if (!order.isCancellable()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CANCELLABLE);
        }
    }

    /**
     * 취소가 확정된 뒤 <b>따라와야 하는 일 전부</b> — 재고 복원 → 적립금 환불 → 쿠폰 복구 → 알림.
     *
     * <p>⚠ 순서가 규약이다(반품 승인과 동일): <b>되돌리기(동기) → 이벤트</b>. 되돌리는 것들은 전부
     * 앞에 오고 이벤트만 뒤에 온다 — 알림이 «취소됐다» 고 말하는 시점에는 재고도 적립금도 쿠폰도
     * 이미 맞아 있어야 한다.
     *
     * <p>🔴 <b>이 메서드가 「되돌리는 것들」의 목록이다.</b> 2026-08-07 에 적립금이 빠져 있었고
     * («반품만 고쳐진» 비대칭), 2026-08-11 에 <b>같은 자리에서 쿠폰이 또 빠져 있었다</b>.
     * 두 번 다 원인이 같다 — <b>되돌릴 것들이 한 줄로 모여 있지 않으면 하나씩 빠진다.</b>
     * → 앞으로 «주문에 붙는 것»(할인·혜택·차감)을 새로 만들면 <b>여기에 되돌리는 줄이 있는지</b>
     * 먼저 본다. 없으면 그 기능은 아직 절반이다.
     * ⚠ 이 목록은 {@link #approveReturn} 과 <b>짝</b>이다. 한쪽에만 넣으면 그게 비대칭의 시작이다.
     */
    private void applyCancellation(Order order) {
        // 🔴 **«남은» 것만 되돌린다** (2026-08-24, G-4 회귀 수정). 부분 취소가 이미 되돌린 몫을
        //    여기서 또 되돌리면 **재고와 돈이 두 번 돌아간다.**
        //    ⚠ 실측(2026-08-24, `20260824-5296`): 적립금 2,000 을 쓴 주문에서 부분 취소가 857·571 을
        //       돌려준 뒤 「주문 취소」가 **원본 2,000 을 또** 돌려줘 합계 3,428 이 나갔다.
        //       재고도 지바 2개 주문에 3개가 복원됐다.
        //    🔴 원인은 이 메서드가 **원본 스냅샷**(`getQuantity`·`getUsedPoint`)을 읽은 것이다 —
        //       부분 취소가 생기기 전에는 «원본 = 남은 것» 이라 구별할 이유가 없었다.
        //    ⚠ 이 주석이 위에서 말하는 «되돌릴 것들이 한 줄로 모여 있지 않으면 하나씩 빠진다» 의
        //       **다른 얼굴**이다: 목록에는 다 있었는데 **양이 틀렸다.**
        order.getItems().stream()
                .filter(it -> it.remainingQuantity() > 0) // 0개를 복원하면 원장에 «안 변했다» 는 거짓 줄이 남는다
                .forEach(it -> productCommandService.increaseStock(
                        it.getVariantId(), it.remainingQuantity(), StockChangeReason.CANCEL, order.getId()));
        pointService.refundCancelledOrder(order.getMemberId(), order.remainingUsedPoint(), order.getId());
        couponService.restore(order.getMemberCouponId());
        eventPublisher.publishEvent(OrderCancelledEvent.from(order));
    }

    /**
     * 반품 요청 (2026-07-24, C-9) — 배송완료 주문만. 본인 주문만(findByIdAndMemberId).
     * 아직 환불·재고 복원은 하지 않는다 — 관리자 승인 때 한다.
     */
    @Transactional
    public void requestReturn(UUID id, UUID memberId, String reason, Map<UUID, Long> quantitiesByItemId) {
        Order order = orderRepository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isReturnRequestable()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_RETURNABLE);
        }
        validateReturnRequest(order, quantitiesByItemId);
        order.requestReturn(reason, quantitiesByItemId);
        log.info("Return requested: {} by {}", id, memberId);
        // 관리자에게 알린다(2026-08-12, 08-11 이월). ⚠ 승인·거절과 달리 **받는 쪽이 관리자**라
        // 구독자가 AdminOrderEventListener 다 — 이벤트는 대상을 모르고 «무슨 일이 있었나» 만 싣는다.
        eventPublisher.publishEvent(OrderReturnRequestedEvent.from(order));
    }

    /**
     * 반품 요청 검증 (2026-08-25, G-10 결정 2 — 고객이 품목·수량을 고른다).
     *
     * <p>🔴 <b>세 가지를 본다</b>: ①그 주문의 품목인가 ②하나 이상 골랐나 ③남은 수량 안인가.
     * ⚠ ②가 없으면 «반품을 요청했는데 아무것도 안 돌아오는» 주문이 생긴다 — 상태만 바뀌고
     * 승인해도 정산이 0 이라, 화면에는 «반품 요청됨» 이 떠 있는데 실제로는 <b>빈 요청</b>이다.
     */
    private void validateReturnRequest(Order order, Map<UUID, Long> quantitiesByItemId) {
        Map<UUID, OrderItem> byId = order.getItems().stream()
                .collect(Collectors.toMap(OrderItem::getId, it -> it));
        long total = 0;
        for (Map.Entry<UUID, Long> e : quantitiesByItemId.entrySet()) {
            OrderItem item = byId.get(e.getKey());
            if (item == null) {
                throw new BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND);
            }
            long qty = e.getValue() == null ? 0L : e.getValue();
            if (qty < 0 || qty > item.remainingQuantity()) {
                throw new BusinessException(ErrorCode.ORDER_RETURN_QUANTITY_INVALID);
            }
            total += qty;
        }
        if (total <= 0) {
            throw new BusinessException(ErrorCode.ORDER_RETURN_ITEM_REQUIRED);
        }
    }

    /**
     * 반품 승인(관리자) — <b>요청된 품목·수량만</b> 재고 복원 + 적립금 환불 (2026-08-25, G-10).
     *
     * <p>재고 복원은 취소와 같고, 환불은 point 도메인 공개 API 로만 한다(도메인 경계).
     * 한 트랜잭션이라 재고·환불·상태가 함께 커밋되거나 함께 롤백된다.
     *
     * <p>🔴 <b>«요청된 것» 을 정산보다 먼저 읽어 둔다.</b> {@link Order#applyRequestedReturns()} 가
     * 요청 수량을 {@code returnedQuantity} 로 옮기면서 0 으로 지우기 때문이다 — 정산 뒤에 읽으면
     * <b>재고도 판매량도 조용히 0 이 된다.</b> 순서가 규약인 자리다.
     *
     * <p>🔴 <b>정산이 낸 값을 그대로 쓴다.</b> 예전에는 {@code order.refundableAmount()} 와
     * {@code order.getEarnedPoint()} 를 다시 읽었는데, 그건 «전량을 반품하면 얼마» 이지
     * «이번에 얼마» 가 아니다 — <b>부분이 생기면 갈린다</b>(WA §1-2-1: 목록이 맞아도 «양»이 틀린다).
     */
    @Transactional
    public void approveReturn(UUID id, AuthUser actor) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isReturnPending()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_RETURN_PENDING);
        }

        // ── 정산 전에 «이번 회차» 를 읽어 둔다 (위 주석의 순서 규약)
        List<OrderItem> requested = order.getItems().stream()
                .filter(it -> it.getReturnRequestedQuantity() > 0)
                .toList();
        if (requested.isEmpty()) {
            throw new BusinessException(ErrorCode.ORDER_RETURN_ITEM_REQUIRED);
        }
        List<StockRestore> restores = requested.stream()
                .map(it -> new StockRestore(it.getVariantId(), it.getReturnRequestedQuantity()))
                .toList();
        List<SoldLine> lines = SoldLine.ofRequestedReturn(order);
        // 🔴 **사유도 여기서 떠 둔다** (2026-08-26, BACKLOG I-10). 지금은 정산이 이 칸을 안 건드리지만
        //    **같은 자리에 두는 것이 규약**이다 — 위 주석이 말하는 «정산 전에 이번 회차를 읽어 둔다».
        //    ⚠ `return_reason` 은 **한 칸**이라 다음 요청이 덮는다. 그래서 회차가 쌓이면
        //       «1회차는 왜 반품했나» 를 알 곳이 **이 원장뿐**이다(거절 쪽이 08-14 에 정한 것과 같은 논리).
        //    ⚠ 🔴 **변형으로 확인했다 — 이 읽기를 정산 «뒤» 로 옮겨도 오늘은 아무 테스트도 안 빨개진다**
        //       (2026-08-26, 0건). 즉 이 배치는 «방어» 이지 지금 지켜지는 계약이 아니다.
        //       그래도 여기 두는 이유: 위 두 줄(`requested`·`lines`)이 **정산 뒤에 읽으면 0 이 되는**
        //       값이라, 같은 블록에 있으면 다음 사람이 «여기는 정산 전» 을 한 번에 읽는다.
        String returnReason = order.getReturnReason();
        String returnedDetail = requested.stream()
                .map(it -> it.getProductName()
                        + (it.getVariantName() == null ? "" : " (" + it.getVariantName() + ")")
                        + " " + it.getReturnRequestedQuantity() + "개")
                .collect(Collectors.joining(", "));

        ReturnSettlement settlement = order.applyRequestedReturns();

        // 물건이 돌아왔으니 재고 복원(취소와 동일 — 옵션 단위).
        // 재고 이력에서는 취소와 구분한다(B-19) — 원장에서 "왜 돌아왔는지"가 구분돼야 값이 있다.
        // ⚠ **이번에 반품된 수량만** (G-10). 예전의 «남은 수량» 은 전량 반품 시절에만 맞던 값이다.
        restores.forEach(r -> productCommandService.increaseStock(
                r.variantId(), r.quantity(), StockChangeReason.RETURN, id));
        // 환불 = 반품금액−쿠폰몫을 적립금으로, 배송완료 적립은 **비례** 회수, 등급 기준에서도 그만큼 차감.
        pointService.refundReturnedOrder(order.getMemberId(), settlement.refundAmount(),
                settlement.earnedToReverse(), settlement.purchaseToRemove(), id);
        // 🔴 쿠폰도 되돌린다 (2026-08-11) — 취소와 **같은 목록**이어야 한다(applyCancellation 참조).
        // ⚠ 환불액이 «상품합계−쿠폰» 인 것과 앞뒤가 맞는다: 할인받은 만큼은 돈으로 안 돌려주니
        //   그 할인의 근거였던 쿠폰을 돌려줘야 고객이 손해를 안 본다. 둘 중 하나만 하면 어느 쪽이든 틀린다.
        // 🔴 **남은 것이 있으면 아직 복구하지 않는다** (G-10) — 쿠폰은 여전히 이 주문에 걸려 있다.
        //   부분 취소가 «마지막 품목이 빠질 때만» 복구하는 것과 같은 규칙이다.
        if (order.hasNothingLeft()) {
            couponService.restore(order.getMemberCouponId());
        }
        // 판매량 되돌림은 catalog 가 구독한다 — 환불(동기)이 끝난 뒤 결과 알림(주문 취소와 같은 규약).
        eventPublisher.publishEvent(
                OrderReturnedEvent.of(order, settlement.refundAmount(), lines, returnedDetail));
        // ⚠ 원장에는 **무엇을 몇 개 되돌리고 얼마를 돌려줬나** 를 적는다. 부분 반품이 생기면서
        //   금액만으로는 «어느 품목이 빠졌나» 를 못 되짚는다(ORDER_ITEM_CANCEL 이 같은 자리에서 정한 것).
        publishAudit(AuditAction.ORDER_RETURN_APPROVE, actor, order,
                returnedDetail + " 반품 / 환불 " + settlement.refundAmount() + "원"
                        + (returnReason == null || returnReason.isBlank()
                                ? "" : " / 사유: " + returnReason));
        log.info("Return approved: {} items={} refund={} earnedReversed={} admin={}",
                id, requested.size(), settlement.refundAmount(), settlement.earnedToReverse(), actor.id());
    }

    /** 반품 승인이 복원할 재고 한 줄 — 정산이 요청 수량을 지우기 전에 떠 둔 스냅샷이다(G-10). */
    private record StockRestore(UUID variantId, long quantity) {
    }

    /**
     * 반품 거절(관리자) — 배송완료로 되돌린다. 재고·적립은 건드리지 않는다(승인 안 했으니).
     *
     * <p>⚠ <b>사유가 필수</b>다(2026-08-11, V47). 거절은 상태를 안 남기므로
     * {@code return_rejected_reason} 이 «거절이 있었다» 를 나타내는 <b>유일한 표시</b>이고,
     * 그게 없으면 고객 화면에서 반품 이야기가 통째로 사라진다.
     */
    @Transactional
    public void rejectReturn(UUID id, AuthUser actor, String reason) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isReturnPending()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_RETURN_PENDING);
        }
        order.rejectReturn(reason);
        // 🔴 고객에게 알린다 (2026-08-11) — 거절은 상태가 조용히 DELIVERED 로 돌아갈 뿐이라
        //    알리지 않으면 «요청해 놓고 영영 소식이 없는» 상태가 된다(08-10 §16-4 4번).
        //    재고·적립금을 안 건드리므로(승인 안 했으니) 구독자는 알림 하나뿐이다.
        // ⚠ 사유를 이벤트에 싣는다 — 알림 문구가 **왜 거절됐는지**를 말해야 한다.
        //    처음(같은 날 오전)엔 «주문 상세에서 확인해 주세요» 로 보냈는데 **그 상세에 아무것도 없었다.**
        eventPublisher.publishEvent(OrderReturnRejectedEvent.from(order));
        // 🔴 원장에 사유를 적는다 (2026-08-14). ⚠ return_rejected_reason 에도 있지만 **그건 현재 상태**다 —
        //    고객이 다시 반품을 요청하면 requestReturn 이 그 칸을 null 로 지운다.
        //    즉 «거절이 있었다» 는 사실이 화면에서 사라지고, 그때 남는 곳이 여기뿐이다.
        publishAudit(AuditAction.ORDER_RETURN_REJECT, actor, order, reason);
        log.info("Return rejected: {} reason={} admin={}", id, reason, actor.id());
    }
}
