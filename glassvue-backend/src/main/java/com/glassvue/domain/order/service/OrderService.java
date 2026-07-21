package com.glassvue.domain.order.service;

import com.glassvue.domain.cart.dto.CartItemResponse;
import com.glassvue.domain.cart.dto.CartResponse;
import com.glassvue.domain.cart.service.CartService;
import com.glassvue.domain.catalog.service.command.ProductCommandService;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.order.dto.AdminOrderResponse;
import com.glassvue.domain.order.dto.OrderResponse;
import com.glassvue.domain.order.dto.OrderSearchCondition;
import com.glassvue.domain.order.entity.Order;
import com.glassvue.domain.order.entity.OrderItem;
import com.glassvue.domain.order.entity.OrderStatus;
import com.glassvue.domain.order.event.OrderCancelledEvent;
import com.glassvue.domain.order.event.OrderPlacedEvent;
import com.glassvue.domain.order.repository.OrderRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.response.PageResponse;
import com.glassvue.global.security.AuthUser;
import org.springframework.context.ApplicationEventPublisher;
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

    /**
     * 장바구니 → 주문 생성. 재고 원자적 차감 + 카트 비우기.
     *
     * <p>{@code AuthUser}를 받는 이유는 닉네임을 주문에 스냅샷으로 남기기 위해서다
     * (탈퇴하면 회원 row가 사라지므로 조회 방식으로는 과거 주문의 구매자를 알 수 없다).
     */
    @Transactional
    public UUID checkout(AuthUser user) {
        UUID memberId = user.id();
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
            orderItems.add(OrderItem.of(i.productId(), i.name(), i.thumbUrl(), i.price(), i.quantity()));
        }

        Order order = orderRepository.save(Order.create(memberId, user.nickname(), orderItems));
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
        return PageResponse.from(page.map(OrderResponse::from));
    }

    /** 관리자 주문 목록 — 전체 주문. 구매자 정보를 포함한 별도 응답을 쓴다. */
    @Transactional(readOnly = true)
    public PageResponse<AdminOrderResponse> adminOrders(OrderSearchCondition condition, Pageable pageable) {
        Page<Order> page = orderRepository.search(condition.forAll(), pageable);
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
        eventPublisher.publishEvent(OrderCancelledEvent.from(order));
        log.info("Order cancelled: {}", id);
    }
}
