package com.glassvue.domain.order.entity;

import com.glassvue.global.common.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// "order"는 Oracle 예약어라 테이블명은 orders.
@Entity
@Getter
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "member_id", columnDefinition = "RAW(16)", nullable = false, updatable = false)
    private UUID memberId;

    /**
     * 구매자 닉네임 스냅샷(주문 시점). member를 직접 참조하지 않는다(도메인 경계).
     *
     * <p>조회 시 member에서 가져오지 않고 저장해두는 이유: {@code MemberService.withdraw}가
     * **하드 삭제**라 탈퇴하면 회원 row가 사라진다. 그때 조회 방식이면 과거 주문의 구매자를
     * 영영 알 수 없게 되는데, 주문은 CS·배송 이력이라 시점 기록이 남아야 한다.
     * {@code Review.author}와 같은 방식.
     */
    @Column(name = "buyer_nickname", nullable = false, length = 50, updatable = false)
    private String buyerNickname;

    /**
     * 사람이 읽는 주문번호(예: {@code 20260723-0026}). PK가 아니라 표시·검색용이다 —
     * PK(UUIDv7)는 고객에게 불러주기 어렵고, 앞자리만 잘라 쓰면 중복 위험이 있다.
     * 형식은 {@code yyyyMMdd}(Asia/Seoul) + 전역 일련번호. 생성 후 바뀌지 않는다.
     */
    @Column(name = "order_no", nullable = false, length = 20, updatable = false)
    private String orderNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    /** 상품 합계(배송비 제외). 이 의미는 바꾸지 않는다 — 바꾸면 과거 주문의 숫자가 무엇인지 알 수 없어진다. */
    @Column(nullable = false)
    private long totalPrice;

    /**
     * 주문 시점에 실제로 부과된 배송비(스냅샷). 정책({@code glassvue.shipping})은 바뀌지만
     * 과거 주문에 받은 금액은 그대로여야 한다 — 배송지·구매자 닉네임과 같은 이유.
     * 배송비 도입 이전 주문은 받은 적이 없으므로 0이 사실이다(모르는 값이 아니다).
     */
    @Column(name = "shipping_fee", nullable = false)
    private long shippingFee;

    private Instant paidAt;

    private Instant shippedAt;

    // --- 배송지 스냅샷 ---
    // 회원의 현재 배송지를 참조하지 않고 주문 시점 값을 복사한다 — 구매자 닉네임·상품 이미지와 같은 이유로,
    // 회원이 나중에 주소를 바꿔도 과거 주문은 "그때 보낸 곳"이어야 CS·배송 이력이 맞다.
    // 기존 주문은 배송지를 알 방법이 없어 nullable(백필 불가). 신규 주문은 요청 검증(@NotBlank)이 보장한다.
    @Column(name = "ship_recipient", length = 50)
    private String shipRecipient;

    @Column(name = "ship_phone", length = 20)
    private String shipPhone;

    @Column(name = "ship_zipcode", length = 10)
    private String shipZipcode;

    @Column(name = "ship_address1", length = 200)
    private String shipAddress1;

    @Column(name = "ship_address2", length = 200)
    private String shipAddress2;

    // 취소 시각. 결제·발송과 마찬가지로 "언제 그렇게 됐는지"가 CS·정산에서 필요하다.
    // updated_at으로는 대체할 수 없다 — 다른 변경에도 갱신되므로 취소 시각이라 단정할 수 없다.
    private Instant cancelledAt;

    // --- 배송 추적(V13) ---
    // 발송 처리는 있었지만 고객이 추적할 방법이 없었다. 배송지(V11)가 "어디로 보낼지"라면 이건 "어떻게 갔는지"다.
    // 이전 주문은 운송장을 알 방법이 없어 nullable(백필 불가) — 화면은 값이 없으면 추적 영역을 감춘다.
    @Enumerated(EnumType.STRING)
    @Column(name = "ship_carrier", length = 30)
    private DeliveryCarrier shipCarrier;

    @Column(name = "ship_tracking_no", length = 50)
    private String shipTrackingNo;

    // 수령 시각. 결제(paid_at)·발송(shipped_at)·취소(cancelled_at)와 같은 성격의 기록이다.
    private Instant deliveredAt;

    // @BatchSize: 목록 조회에서 주문마다 items를 따로 읽는 N+1을 막는다(IN 쿼리 한 번으로 묶음).
    // 컬렉션 fetch join은 페이징과 같이 쓰면 전체를 메모리에 올리므로(HHH000104) 쓰지 않는다.
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    private Order(UUID memberId, String buyerNickname, String orderNo) {
        this.memberId = memberId;
        this.buyerNickname = buyerNickname;
        this.orderNo = orderNo;
        this.status = OrderStatus.ORDERED;
        this.totalPrice = 0L;
    }

    /** 배송지는 주문 시점 스냅샷으로 받는다(회원 주소를 참조하지 않는다). */
    public static Order create(UUID memberId, String buyerNickname, List<OrderItem> orderItems,
                               String shipRecipient, String shipPhone,
                               String shipZipcode, String shipAddress1, String shipAddress2,
                               long shippingFee, String orderNo) {
        Order order = new Order(memberId, buyerNickname, orderNo);
        order.shippingFee = shippingFee;
        order.shipRecipient = shipRecipient;
        order.shipPhone = shipPhone;
        order.shipZipcode = shipZipcode;
        order.shipAddress1 = shipAddress1;
        order.shipAddress2 = shipAddress2;
        orderItems.forEach(order::addItem);
        return order;
    }

    private void addItem(OrderItem item) {
        items.add(item);
        item.assignOrder(this);
        this.totalPrice += item.getLineTotal();
    }

    /** 실제 결제 금액 = 상품 합계 + 배송비. 저장하지 않고 계산한다(두 값이 어긋날 여지를 만들지 않는다). */
    public long getPayAmount() {
        return totalPrice + shippingFee;
    }

    public boolean isPayable() {
        return status == OrderStatus.ORDERED;
    }

    public boolean isShippable() {
        return status == OrderStatus.PAID;
    }

    /** 배송완료 처리 가능: 발송된 주문만. */
    public boolean isDeliverable() {
        return status == OrderStatus.SHIPPED;
    }

    /**
     * 취소 가능: 결제 전(ORDERED) 또는 결제 후 미발송(PAID)까지. 발송(SHIPPED)·수령(DELIVERED)되면 불가.
     *
     * <p>새 상태가 자동으로 포함되지 않도록 {@code <> CANCELLED}가 아니라 **명시적 열거**로 둔다 —
     * 7/16에 상태를 확장하며 리뷰 구매인증 범위가 조용히 어긋난 적이 있다(ARCHITECTURE §5).
     * 실제로 이번에 DELIVERED가 늘었지만 이 메서드는 손댈 필요가 없었다.
     */
    public boolean isCancellable() {
        return status == OrderStatus.ORDERED || status == OrderStatus.PAID;
    }

    /** 결제 완료 처리. (실제 결제는 이후 PG 연동으로 대체 — 지금은 상태 전이만) */
    public void pay() {
        this.status = OrderStatus.PAID;
        this.paidAt = Instant.now();
    }

    /**
     * 발송 처리(관리자). 운송장은 **필수** — 택배사·송장번호 없이 발송하면 고객이 추적할 수 없고,
     * 나중에 채워 넣을 경로도 없어 그 주문은 영영 "보냈다"는 사실만 남는다.
     */
    public void ship(DeliveryCarrier carrier, String trackingNo) {
        this.status = OrderStatus.SHIPPED;
        this.shippedAt = Instant.now();
        this.shipCarrier = carrier;
        this.shipTrackingNo = trackingNo;
    }

    /** 배송완료(수령) 처리(관리자). SHIPPED에서만. */
    public void deliver() {
        this.status = OrderStatus.DELIVERED;
        this.deliveredAt = Instant.now();
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = Instant.now();
    }

    public boolean isOwnedBy(UUID memberId) {
        return this.memberId.equals(memberId);
    }
}
