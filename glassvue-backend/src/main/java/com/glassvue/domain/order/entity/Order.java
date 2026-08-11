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

    /**
     * 사용한 쿠폰의 이름·할인액 스냅샷(V17). 쿠폰 정의가 바뀌거나 삭제돼도 주문 내역은
     * "그때 얼마 할인받았는지"를 그대로 보여줘야 한다 — 배송비·정가와 같은 판단.
     * 쿠폰을 안 쓴 주문은 이름이 null, 할인액이 0 이다(모르는 값이 아니라 0 이다).
     */
    @Column(name = "coupon_name", length = 100, updatable = false)
    private String couponName;

    @Column(name = "coupon_discount", nullable = false, updatable = false)
    private long couponDiscount;

    /**
     * 사용한 <b>발급쿠폰(member_coupon) id</b> — 취소·반품 때 되돌릴 대상 (2026-08-11, V46).
     *
     * <p>⚠ 위 둘과 성격이 다르다. {@code couponName}·{@code couponDiscount} 는 «대상이 사라져도
     * 읽혀야 하는» <b>스냅샷</b>이고, 이건 «되돌리려고 가리키는» <b>참조</b>다. 그래서 화면에 안 나간다
     * (이름은 계속 {@code couponName} 을 쓴다).
     *
     * <p>⚠ 이 값이 없던 동안 <b>취소해도 쿠폰이 안 돌아왔다</b> — 적립금은 금액만으로 되돌릴 수 있지만
     * 쿠폰은 <b>어느 장인지</b>를 알아야 한다. 같은 회원이 같은 쿠폰을 여러 장 받을 수 있어
     * ({@code CouponService.issue}) 이름으로는 못 찾는다.
     *
     * <p>⚠ FK 는 없다 — 탈퇴하면 발급쿠폰이 통째로 지워지는데(F-1) FK 가 있으면 그 삭제를 주문이 막는다.
     * NULL 은 «안 썼거나 V46 이전 주문»이고, {@code couponDiscount > 0} 과 함께 보면 갈린다.
     */
    @Column(name = "member_coupon_id", updatable = false)
    private UUID memberCouponId;

    /**
     * 이 주문에 쓴 적립금 · 이 주문으로 받은 적립금 — 둘 다 <b>스냅샷</b>이다 (2026-07-24, V21).
     * 적립률이 나중에 바뀌어도 "그때 얼마 받았는지"는 이 값이 사실이다
     * (구매자 닉네임 V5 · 배송비 V14 · 정가 V16 · 쿠폰 V17 과 같은 원칙).
     */
    @Column(name = "used_point", nullable = false)
    private long usedPoint;

    @Column(name = "earned_point", nullable = false)
    private long earnedPoint;

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

    /**
     * 배송 요청사항 (V38, 2026-08-03 B-20) — <b>주문 시점 스냅샷</b>이다.
     *
     * <p>배송지 5필드와 같은 성격이라 나란히 둔다: 회원이 나중에 주소록을 고쳐도
     * <b>과거 주문의 요청사항은 그대로여야</b> CS 가 맞는다.
     *
     * <p>{@code null} 이 정상값이다 — 요청이 없는 주문이 대부분이고, V38 이전 주문은
     * <b>실제로 요청사항이 없었다</b>(백필하지 않는 이유).
     */
    @Column(name = "ship_memo", length = 200)
    private String shipMemo;

    // 취소 시각. 결제·발송과 마찬가지로 "언제 그렇게 됐는지"가 CS·정산에서 필요하다.
    // updated_at으로는 대체할 수 없다 — 다른 변경에도 갱신되므로 취소 시각이라 단정할 수 없다.
    private Instant cancelledAt;

    /**
     * 취소 사유 (2026-08-04, V40, 백로그 B-17) — <b>선택</b>이다.
     *
     * <p>반품에는 사유가 있는데 취소에는 없어 관리자가 "왜 취소됐는지" 를 알 수 없었다.
     * 길이·semantics 는 {@link #returnReason} 과 <b>같게</b> 맞춘다 — 같은 성격의 값이라 갈리면
     * 한쪽에서 되던 입력이 다른 쪽에서 터진다(WA §2-2-1).
     *
     * <p>🔴 <b>2026-08-10 정정</b>: 여기 *"행위자 컬럼은 두지 않는다 — 취소자는 항상 주문자 본인이다"*
     * 라고 적혀 있었다. <b>B-25 가 그 전제를 깼다</b> — 관리자도 취소할 수 있게 되면서 아래
     * {@link #cancelledBy} 가 생겼다. 전제에 기대어 «안 만든다» 고 적은 주석은 전제가 바뀌면
     * <b>틀린 설명으로 남는다</b>. 그래서 지우지 않고 정정해 둔다.
     *
     * <p>⚠ 사유의 <b>필수 여부는 누가 취소하느냐로 갈린다</b>: 본인 취소는 선택(위), 관리자 취소는
     * <b>필수</b>다({@code AdminOrderCancelRequest}). 고객은 자기가 왜 취소했는지 알지만,
     * 남이 취소한 주문은 <b>사유가 유일한 단서</b>다.
     */
    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    /**
     * 취소한 관리자 id — <b>관리자 취소일 때만</b> 채워진다 (2026-08-10, V43, 백로그 B-25).
     *
     * <p>⚠ <b>NULL 이 «본인이 취소했다» 는 뜻</b>이다. 별도 플래그를 두지 않은 이유: 플래그와 id 가
     * 어긋난 행(플래그는 true 인데 id 가 NULL 등)이 생길 수 있고, 그런 행은 <b>앱이 멀쩡히 돌면서</b>
     * 화면에만 이상하게 나온다(G-3 의 {@code product_id}·{@code inquiry_type} 쌍에서 겪은 그 모양).
     * 값 하나면 어긋날 자리가 없다.
     * <p>⚠ 기존 취소 주문은 <b>백필하지 않는다</b> — 관리자 취소가 없던 시절이라 NULL 이 **사실**이다
     * (V40·V39 와 같은 판단. V41 이 백필한 것은 «아니다» 가 사실이어서였다).
     */
    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    /**
     * 취소한 관리자 닉네임 <b>스냅샷</b> (2026-08-10, V43).
     *
     * <p>⚠ id 만으로는 못 보여준다 — 관리자 계정은 <b>강제 삭제될 수 있고</b>(B-24) 닉네임도 바뀐다.
     * 감사 로그가 {@code actor_name}·{@code target_login} 을 스냅샷으로 뜨는 것과 <b>같은 이유·같은 방식</b>이다.
     * 조인으로 풀면 관리자가 지워진 순간 «누가 취소했는지» 가 화면에서 사라진다.
     */
    @Column(name = "cancelled_by_name", length = 50)
    private String cancelledByName;

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

    // --- 반품(V24, C-9) ---
    // 배송완료 주문을 고객이 반품 요청 → 관리자 승인. 사유·시각을 남긴다(CS·정산 근거).
    @Column(name = "return_reason", length = 500)
    private String returnReason;

    @Column(name = "return_requested_at")
    private Instant returnRequestedAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

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
                               String shipMemo,
                               long shippingFee, String orderNo,
                               String couponName, long couponDiscount, UUID memberCouponId,
                               long usedPoint) {
        Order order = new Order(memberId, buyerNickname, orderNo);
        order.shippingFee = shippingFee;
        order.couponName = couponName;
        order.couponDiscount = couponDiscount;
        order.memberCouponId = memberCouponId;
        order.usedPoint = usedPoint;
        order.shipRecipient = shipRecipient;
        order.shipPhone = shipPhone;
        order.shipZipcode = shipZipcode;
        order.shipAddress1 = shipAddress1;
        order.shipAddress2 = shipAddress2;
        order.shipMemo = shipMemo;
        orderItems.forEach(order::addItem);
        return order;
    }

    private void addItem(OrderItem item) {
        items.add(item);
        item.assignOrder(this);
        this.totalPrice += item.getLineTotal();
    }

    /**
     * 실제 결제 금액 = 상품합계 − 쿠폰할인 + 배송비. 저장하지 않고 계산한다
     * (저장하면 구성 요소와 어긋날 여지가 생긴다).
     *
     * <p>배송비는 <b>할인 전</b> 상품합계로 정해진다 — 쿠폰을 썼다고 배송비가 붙으면
     * 고객이 손해 본 기분이 든다(2026-07-23 결정). 그래서 이 식의 순서가 곧 정책이다.
     */
    public long getPayAmount() {
        return totalPrice - couponDiscount - usedPoint + shippingFee;
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

    /**
     * 적립·등급 산정의 기준액 — <b>실제로 낸 상품 대금</b>이다.
     *
     * <p>배송비를 빼는 이유: 운임이지 상품 대금이 아니다.
     * 사용한 적립금을 빼는 이유: 적립금으로 낸 부분에까지 적립을 주면 <b>포인트가 포인트를 낳는다.</b>
     * 계산을 여기 두는 것은 <b>구독자가 주문 금액 규칙을 몰라도 되게</b> 하기 위해서다(도메인 경계).
     */
    public long rewardableAmount() {
        return Math.max(0L, totalPrice - couponDiscount - usedPoint);
    }

    /** 배송완료 적립이 실제로 얼마였는지 주문에 스냅샷한다. */
    public void recordEarnedPoint(long earned) {
        this.earnedPoint = Math.max(0L, earned);
    }

    /**
     * 주문 취소. {@code reason} 은 <b>선택</b>이라 null·공백이면 저장하지 않는다(B-17).
     *
     * <p>공백만 적힌 사유를 그대로 넣으면 화면이 <b>"사유가 있다"</b> 로 읽어 빈 칸을 그린다 —
     * 없는 것과 있는데 비어 보이는 것은 다르게 다뤄야 하므로 여기서 NULL 로 눕힌다.
     * {@code requestReturn} 은 사유가 필수라 이 가드가 없다(그쪽은 화면·DTO 가 빈 값을 막는다).
     */
    /** 본인 취소. 행위자 컬럼은 비워 둔다 — <b>NULL 이 «주문자 본인» 을 뜻한다</b>({@link #cancelledBy}). */
    public void cancel(String reason) {
        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = Instant.now();
        this.cancelReason = (reason == null || reason.isBlank()) ? null : reason.trim();
    }

    /**
     * 관리자 대행 취소 (2026-08-10, B-25). 상태 전이는 본인 취소와 <b>완전히 같고</b> 행위자만 더 남는다.
     *
     * <p>⚠ {@link #cancel} 을 부르고 행위자를 덧칠하는 식으로 쓰지 않는다 — 두 줄로 나뉘면
     * <b>한쪽만 부른 호출부</b>가 생길 수 있고, 그러면 «관리자가 취소했는데 NULL 인» 행이 남는다.
     * 그 행은 위 {@link #cancelledBy} 규칙상 «본인이 취소했다» 로 <b>거짓말을 한다.</b>
     * 한 메서드 안에서 함께 정해야 어긋날 자리가 없다.
     *
     * @param reason    <b>필수</b>다(호출부 DTO 가 {@code @NotBlank} 로 막는다). 남이 취소한 주문은
     *                  사유가 유일한 단서라, 여기서도 빈 값이면 그대로 두지 않고 막는다.
     * @param adminName 닉네임 스냅샷 — 관리자가 지워지거나 개명해도 남아야 한다.
     */
    public void cancelByAdmin(String reason, UUID adminId, String adminName) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("관리자 취소에는 사유가 필요하다");
        }
        cancel(reason);
        this.cancelledBy = adminId;
        this.cancelledByName = adminName;
    }

    /** 배송완료 주문만 반품 요청할 수 있다(운송 중·미결제 주문은 취소로 처리). */
    public boolean isReturnRequestable() {
        return status == OrderStatus.DELIVERED;
    }

    /** 요청된 반품만 승인·거절할 수 있다. */
    public boolean isReturnPending() {
        return status == OrderStatus.RETURN_REQUESTED;
    }

    public void requestReturn(String reason) {
        this.status = OrderStatus.RETURN_REQUESTED;
        this.returnReason = reason;
        this.returnRequestedAt = Instant.now();
    }

    /** 관리자 승인 — 재고 복원·환불은 서비스가 하고, 여기선 상태·시각만 남긴다. */
    public void approveReturn() {
        this.status = OrderStatus.RETURNED;
        this.returnedAt = Instant.now();
    }

    /** 관리자 거절 — 배송완료 상태로 되돌린다. 사유는 기록으로 남겨 둔다(요청이 있었다는 흔적). */
    public void rejectReturn() {
        this.status = OrderStatus.DELIVERED;
        this.returnRequestedAt = null;
    }

    /**
     * 반품 환불액 = 상품합계 − 쿠폰할인. 사용했던 적립금 + 현금분을 한꺼번에 적립금으로 돌려준다.
     * 배송비는 뺀다(운임은 소진됐다). 이 값과 적립 회수(earned_point)를 합쳐 순변동이 결정된다.
     */
    public long refundableAmount() {
        return Math.max(0L, totalPrice - couponDiscount);
    }

    public boolean isOwnedBy(UUID memberId) {
        return this.memberId.equals(memberId);
    }
}
