package com.glassvue.domain.order.entity;

import com.glassvue.global.common.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    /**
     * 반품 <b>거절</b> 사유·시각 (2026-08-11, V47).
     *
     * <p>⚠ <b>거절은 상태를 남기지 않는다</b> — {@code DELIVERED} 로 되돌아가므로, 이 값이
     * «거절이 있었다» 를 나타내는 <b>유일한 표시</b>다. 화면도 이걸로 반품 카드를 띄운다.
     *
     * <p>🔴 <b>왜 뒤늦게 생겼나</b>: 같은 날 붙인 거절 알림이 *"자세한 내용은 주문 상세에서
     * 확인해 주세요"* 라고 말하는데 <b>주문 상세에 아무것도 없었다.</b> 사유가 없는 정도가 아니라
     * 반품 카드가 아예 안 떴고({@code DELIVERED} 는 렌더 조건 밖), {@code returnRequestedAt} 까지
     * 지워져 <b>요청한 적 없는 주문과 구분이 안 됐다.</b>
     *
     * <p>⚠ 사유는 <b>필수</b>다(요청 DTO 가 강제). 고객은 자기가 왜 요청했는지 알지만
     * <b>남이 거절한 이유는 사유가 유일한 단서</b>다 — 관리자 취소({@code cancelReason})와 같은 판단.
     */
    @Column(name = "return_rejected_reason", length = 500)
    private String returnRejectedReason;

    @Column(name = "return_rejected_at")
    private Instant returnRejectedAt;

    // @BatchSize: 목록 조회에서 주문마다 items를 따로 읽는 N+1을 막는다(IN 쿼리 한 번으로 묶음).
    // 컬렉션 fetch join은 페이징과 같이 쓰면 전체를 메모리에 올리므로(HHH000104) 쓰지 않는다.
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    /*
     * ─────────────────────────── 부분 취소로 «회수된 몫» 누적 (2026-08-24, V57, BACKLOG G-4)
     *
     * 🔴 **원본 셋(total_price·coupon_discount·used_point)은 건드리지 않는다.** total_price 는 위에서
     *    *"이 의미는 바꾸지 않는다"* 고 못박혀 있고 coupon_discount 는 아예 updatable = false 다.
     *    → 지금 받을 금액은 **뺄셈으로** 얻는다(remainingXxx / getPayAmount).
     *
     * ⚠ **셋을 따로 두는 이유는 배분이 «경로 의존» 이기 때문이다.** 내림으로 나누므로 어느 품목을
     *    먼저 취소했느냐에 따라 1원이 다른 자리에 남는다 — 남은 값에서 다시 계산하려면
     *    «지금까지 얼마를 떼어 갔나» 가 실제로 저장돼 있어야 한다. 유도할 수 있는 값이 아니다.
     */

    /** 부분 취소로 빠진 상품금액 누적. 남은 상품합계 = {@code totalPrice - 이 값}. */
    @Column(name = "cancelled_items_total", nullable = false)
    private long cancelledItemsTotal;

    /** 부분 취소로 회수된 쿠폰 할인 몫 누적(금액 비례·내림). */
    @Column(name = "cancelled_coupon_discount", nullable = false)
    private long cancelledCouponDiscount;

    /** 부분 취소로 되돌린 사용 적립금 몫 누적(금액 비례·내림). */
    @Column(name = "cancelled_point", nullable = false)
    private long cancelledPoint;

    /* ───────────────────── 부분 반품으로 회수된 몫 (2026-08-25, V58, BACKLOG G-10) ─────────────────────
     *
     * 🔴 위 `cancelled_*` 셋과 **칸을 나눈 이유**는 G-10 결정 1 이다 — 취소와 반품은 돈이 다르게
     *    움직인다. 취소 환불은 «쓴 적립금만» 계정으로 돌아가고(돈은 seam), 반품 환불은
     *    «현금결제분 + 사용적립금» 을 **함께** 적립금으로 준다. 합치면 주문만 보고 못 가른다.
     */

    /** 반품으로 빠진 상품금액 누적. 남은 상품합계 = {@code totalPrice - cancelledItemsTotal - 이 값}. */
    @Column(name = "returned_items_total", nullable = false)
    private long returnedItemsTotal;

    /** 반품으로 회수된 쿠폰 할인 몫 누적(금액 비례·내림). */
    @Column(name = "returned_coupon_discount", nullable = false)
    private long returnedCouponDiscount;

    /**
     * 반품으로 회수된 사용 적립금 몫 누적(금액 비례·내림).
     *
     * <p>🔴 <b>환불 계산에는 안 쓰인다</b> — 반품 환불액(«상품합계 − 쿠폰»)에 이 몫이 <b>이미
     * 들어 있다.</b> 그런데도 누적하는 이유는 {@link #remainingUsedPoint()} 를 낮춰야 다음 회차의
     * 분모와 {@link #rewardableAmount()} 가 맞기 때문이다.
     * ⚠ <b>취소 쪽 {@code cancelledPoint} 와 쓰임이 다르다</b> — 그쪽은 실제로 계정에 돌려줄 금액이다.
     */
    @Column(name = "returned_point", nullable = false)
    private long returnedPoint;

    /**
     * 반품으로 회수한 <b>배송완료 적립</b> 누적 (V58).
     *
     * <p>🔴 <b>왜 칸이 필요한가</b> — 전량 반품이던 시절엔 {@link #earnedPoint} 전액을 회수하면 됐지만,
     * 부분이 생기면 «지금까지 얼마 회수했나» 를 알아야 다음 회차를 계산할 수 있다.
     * ⚠ 내림 배분은 <b>경로 의존</b>이라 어느 품목을 먼저 반품했느냐에 따라 1원이 다른 자리에 남는다 —
     * <b>유도할 수 있는 값이 아니다.</b> {@code cancelledPoint} 를 둔 것과 글자 그대로 같은 이유다.
     */
    @Column(name = "reversed_earned_point", nullable = false)
    private long reversedEarnedPoint;

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
        // 🔴 **품목이 하나도 안 남으면 0 이다** (2026-08-24). 안 그러면 전량이 빠진 주문에
        //    **배송비만 덩그러니** 남아 「남은 결제 금액 3,000원」으로 보인다 —
        //    실측(`20260824-5297`)에서 그렇게 나왔다. 취소된 주문에 배송비를 받을 이유가 없다.
        //    ⚠ 부분 취소를 한 적 없는 주문은 여기 안 걸린다(`cancelledQuantity` 가 전부 0) —
        //       그래서 **기존 취소 주문의 표시는 안 바뀐다**(예전처럼 «결제했던 금액» 을 보여준다).
        // 🔴 **반품으로 비워진 주문은 여기 안 걸린다** (2026-08-25, G-10). 그때는 아래 식이
        //    남은 값 전부 0 + 배송비 = **배송비만** 낸다 — 그리고 **그게 맞다**: 반품은 배송비를
        //    안 돌려주므로(G-10 결정 3, 물건이 이미 나갔다) 고객이 실제로 낸 것이 배송비뿐이다.
        //    ⚠ 취소는 반대다. 물건이 안 나갔으니 배송비를 받을 이유가 없어 0 이다.
        //    ⚠ **기존 전체 반품 주문의 표시는 안 바뀐다** — `returned_quantity` 가 0 이라
        //       `remainingItemsTotal()` 이 그대로다(V57 이 «기존 취소 주문은 안 바뀐다» 로 간 것과 같다).
        if (isFullyCancelledByItems()) {
            return 0;
        }
        return remainingItemsTotal() - remainingCouponDiscount() - remainingUsedPoint() + shippingFee;
    }

    /* ─────────────────────────── 부분 취소 뒤의 «지금» 값 (G-4) ─────────────────────────── */

    /**
     * 아직 살아 있는 상품합계. 부분 취소·부분 반품이 없으면 {@code totalPrice} 와 같다.
     *
     * <p>🔴 <b>되돌리는 경로 «둘» 을 다 뺀다</b> (2026-08-25, G-10). 한쪽만 빼면 이미 나간 물건이
     * 아직 있는 것처럼 보인다 — WA §1-2-1 이 «읽는 값이 원본인지 남은 것인지» 를 대조하라는 자리다.
     */
    public long remainingItemsTotal() {
        return totalPrice - cancelledItemsTotal - returnedItemsTotal;
    }

    /** 아직 걸려 있는 쿠폰 할인. 🔴 쿠폰 최소금액은 <b>소급하지 않는다</b>(G-4 결정 1 · G-10 동일). */
    public long remainingCouponDiscount() {
        return couponDiscount - cancelledCouponDiscount - returnedCouponDiscount;
    }

    /** 아직 이 주문에 묶여 있는 사용 적립금. */
    public long remainingUsedPoint() {
        return usedPoint - cancelledPoint - returnedPoint;
    }

    /** 아직 회수하지 않은 배송완료 적립. 부분 반품의 회수 몫은 여기서 비례로 떼어 간다(G-10). */
    public long remainingEarnedPoint() {
        return earnedPoint - reversedEarnedPoint;
    }

    /**
     * 부분 취소로 지금까지 돌려준 <b>돈</b>의 누적.
     *
     * <p>⚠ 적립금으로 돌아간 몫({@link #getCancelledPoint()})은 여기 안 들어간다 — 그건 계정으로
     * 돌아가지 돈으로 나가지 않는다. 고객이 되찾은 값어치의 합은 «이 값 + 되돌린 적립금» 이다.
     */
    public long refundedAmount() {
        // 전량이 빠졌으면 배송비도 돌아간다 — 위 {@link #getPayAmount()} 와 앞뒤가 맞아야 한다
        // (돌려준 것 + 남은 것 = 처음 결제한 것).
        return cancelledItemsTotal - cancelledCouponDiscount - cancelledPoint
                + (isFullyCancelledByItems() ? shippingFee : 0);
    }

    /** 반품으로 적립금으로 돌려준 금액의 누적 = 반품 상품금액 − 회수한 쿠폰 몫 (G-10). */
    public long returnRefundedAmount() {
        return returnedItemsTotal - returnedCouponDiscount;
    }

    /**
     * 품목이 <b>전부 취소로</b> 빠졌나 — <b>배송비 환불 스위치</b>다.
     *
     * <p>🔴 <b>반품은 안 센다</b> (2026-08-25, G-10). 이름을 {@code hasNoRemainingItems} 에서 바꾼
     * 이유가 그것이다 — 옛 이름은 «남은 게 없다» 라고 읽히는데 실제로 세는 것은 <b>취소분뿐</b>이라,
     * 반품이 생기는 순간 <b>이름이 거짓말을 시작한다.</b> 배송비를 돌려주는 것은 취소일 때뿐이고
     * (G-10 결정 3), 여기에 반품을 더하면 «반품했더니 배송비까지 돌아오는» 동작이 조용히 생긴다.
     * <p>남은 것이 하나도 없는지는 {@link #hasNothingLeft()} 가 답한다.
     */
    public boolean isFullyCancelledByItems() {
        // ⚠ `allMatch` 는 빈 목록에 **참**이다. 주문에 품목이 없을 수는 없지만, 이 값이 지금은
        //   `getPayAmount()` 를 0 으로 만드는 스위치라 빈 목록이 조용히 0 을 내지 않게 막는다.
        return !items.isEmpty() && items.stream().allMatch(OrderItem::isFullyCancelled);
    }

    /**
     * 남은 것이 하나도 없나 — <b>취소든 반품이든</b> 빠진 것을 다 센다 (G-10).
     *
     * <p>반품 승인이 주문을 {@code RETURNED} 로 떨어뜨릴지 {@code DELIVERED} 로 되돌릴지 이 값이 정한다.
     */
    public boolean hasNothingLeft() {
        return !items.isEmpty() && items.stream().allMatch(OrderItem::isFullyGone);
    }

    /**
     * 🔴 <b>품목 하나에서 {@code qty} 개를 취소하고 정산을 나눈다</b> (BACKLOG G-4).
     *
     * <p><b>배분식</b> — 규칙 원본은 BACKLOG G-4 「결정 (2026-08-24, 사용자 확정)」이다:
     * <pre>
     *   취소금액   = 단가 × qty
     *   쿠폰 몫    = 남은쿠폰할인 × 취소금액 / 남은상품합계   (내림)
     *   적립금 몫  = 남은적립금   × 취소금액 / 남은상품합계   (내림)
     *   환불액     = 취소금액 − 쿠폰 몫 − 적립금 몫
     * </pre>
     *
     * <p>🔴 <b>분모·분자가 «원본» 이 아니라 «지금 남은 값» 이다.</b> 그래서 내림으로 버려진 잔돈이
     * 주문에 남아 있다가 <b>다음 취소로 따라간다</b> — 마지막 품목을 취소할 때는 분모와 분자가 같아져
     * 남은 몫이 <b>전부</b> 넘어간다. 즉 <b>전액 수렴이 구조로 보장된다</b>: 품목을 하나씩 다 취소하면
     * 환불 합계가 정확히 결제금액이 된다(G-4 검산 — 1,000원을 셋에 나누면 333·333·<b>334</b>).
     *
     * <p>⚠ <b>배송비는 손대지 않는다</b>(G-4 결정 2). 품목을 빼면 상품합계가 낮아지므로
     * {@code feeFor} 는 «0 → 3,000» 방향으로만 움직인다 — 「취소했더니 돈을 더 냈다」를 만들지 않는다.
     *
     * @return 이 회차에 돌려줄 <b>돈</b>. 되돌릴 적립금은 {@code before/after} 차이로 호출부가 읽는다.
     */

    /**
     * 🔴 <b>내림 배분이 남긴 잔돈이 «현금» 으로 새지 않게 막는다</b> (2026-09-02, BACKLOG §K-3 · I-11).
     *
     * <p><b>무엇이 문제였나</b>: 결제 시 상한이 {@code 상품합계 − 쿠폰할인} 이라 주문 시점엔
     * {@code 쿠폰 + 적립금 ≤ 상품합계} 다. 그런데 쿠폰 몫과 적립금 몫을 <b>각각</b> 내림하면
     * 둘 다 «덜» 빠져서, 남은 쿠폰+적립금이 남은 상품합계를 <b>앞지른다.</b>
     * 앞지른 만큼은 그 회차에 <b>현금 환불로 나가 버린다</b> — 현금이 한 푼도 없는 주문인데도 그렇다.
     * 그리고 그 빚은 마지막 회차가 떠안아 <b>환불액이 음수</b>가 된다.
     *
     * <p><b>실측 (10,000×3 · 쿠폰 10,000 · 적립금 20,000 → 결제금액 0)</b>:
     * 회차별 환불액이 {@code [1, 0, -1]} 이었다. ⚠ <b>합계는 0 으로 맞았다</b> — 그래서
     * 「전액 수렴」 테스트로는 안 잡혔다. 🔴 <b>«회차별로 말이 되는가» 는 «합계가 맞는가» 와
     * 다른 질문이다.</b>
     *
     * <p><b>어디를 고쳤나</b>: 마지막 회차의 음수가 아니라 <b>첫 회차의 «+1»</b> 이다.
     * 애초에 그 1 원을 현금으로 내보내지 않으면 빚이 안 생긴다.
     *
     * <p>⚠ <b>적립금 몫에 붙이는 이유</b>: 적립금 몫은 {@code refundCancelledOrder} 로
     * <b>실제 고객에게 돌아가는 수량</b>이고, 쿠폰 몫은 «얼마짜리 쿠폰이었나» 의 회수 기록이다.
     * 잔돈을 «돌려주는 쪽» 에 붙이면 현금 1 원이 적립금 1 원으로 바뀔 뿐이라
     * <b>고객이 받는 총액이 회차 단위로도 말이 된다.</b>
     *
     * <p>🔴 <b>정상 주문에는 아무 일도 안 한다.</b> {@code over <= 0} 이면 그대로다 —
     * G-4·G-10 의 확정 표본(12,001 · 12,858 …)이 <b>한 자리도 안 바뀐다.</b>
     * 이 보정은 <b>부등식이 실제로 깨질 때만</b> 켜진다.
     *
     * @return {@code pointShare} 에 더해야 할 몫 (0 이상)
     */
    private static long residueIntoPoint(long remCoupon, long remPoint,
                                         long couponShare, long pointShare,
                                         long base, long amount) {
        // «다음 회차로 넘길 쿠폰+적립금» 이 «다음 회차의 상품합계» 를 얼마나 앞지르는가.
        long over = (remCoupon - couponShare) + (remPoint - pointShare) - (base - amount);
        return Math.max(0L, over);
    }

    public long cancelItem(OrderItem item, long qty) {
        long base = remainingItemsTotal();
        if (base <= 0) {
            throw new IllegalStateException("남은 품목이 없는 주문에서 부분 취소를 시도했다: " + orderNo);
        }
        long cancelledAmount = item.cancel(qty);
        // ⚠ 내림이다(정수 나눗셈). 반올림하면 여러 품목에서 겹칠 때 합이 원래 할인액을 넘거나 모자라
        //    전액 취소 시 결제금액과 어긋난다 — G-4 가 반올림을 고르지 않은 이유가 그것이다.
        long remCoupon = remainingCouponDiscount();
        long remPoint = remainingUsedPoint();
        long couponShare = remCoupon * cancelledAmount / base;
        long pointShare = remPoint * cancelledAmount / base;
        // 🔴 잔돈이 현금으로 새지 않게 — 부등식이 깨질 때만 켜진다(§K-3).
        pointShare += residueIntoPoint(remCoupon, remPoint, couponShare, pointShare, base, cancelledAmount);

        this.cancelledItemsTotal += cancelledAmount;
        this.cancelledCouponDiscount += couponShare;
        this.cancelledPoint += pointShare;
        return cancelledAmount - couponShare - pointShare;
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
        // ⚠ 부분 취소가 있었으면 **남은 것** 기준이다(G-4). 취소된 몫에 적립을 주면
        //    돌려준 돈에 적립이 붙는다. 적립은 배송완료에 일어나고 부분 취소는 그전(ORDERED·PAID)
        //    에만 되므로, 여기 닿을 때는 회수할 적립이 아직 없다 — **줄 것을 덜 주는 쪽**이다.
        return Math.max(0L, remainingItemsTotal() - remainingCouponDiscount() - remainingUsedPoint());
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

    /**
     * 🔴 <b>관리자의 부분 취소가 마지막 품목까지 비웠을 때</b> (2026-08-26, BACKLOG I-6).
     *
     * <p>⚠ <b>{@link #cancelByAdmin} 과 갈라 둔 이유는 «사유» 하나다.</b> 그쪽은 사유가 <b>필수</b>인데
     * 부분 취소는 <b>사유를 아예 받지 않는다</b>({@code OrderItemCancelRequest} — «회차마다 일어나
     * 한 칸에 담기지 않는다» 는 G-4 결정). 없는 사유를 지어 넣지 않는다:
     * <b>«관리자 부분 취소로 비었음» 같은 문구는 관리자가 «쓴» 사유가 아니라 서버가 만든 설명</b>이라,
     * 화면의 「취소 사유」 칸에 넣으면 <b>사람이 적은 것처럼 보인다</b>(V37 이 동의 시각을 백필하지 않은 것과
     * 같은 판단 — 지어낸 값은 근거로 쓰이는 순간 틀린 결정을 만든다).
     * → <b>사유는 NULL 로 두고 행위자만 남긴다.</b> 「무엇을 몇 개」 는 원장({@code ORDER_ITEM_CANCEL})에
     * 회차별로 있다.
     *
     * <p>🔴 <b>왜 필요한가</b>: 이 갈래가 {@link #cancel}{@code (null)} 을 부르고 있었다 —
     * 관리자가 대행으로 마지막 품목을 뺐는데도 {@link #cancelledBy} 가 NULL 로 남아,
     * 위 규칙상 <b>«주문자 본인이 취소했다» 로 거짓말</b>을 했다. 고객 상세의
     * 「고객센터에서 대신 취소했어요」 줄도 안 떴다.
     * ⚠ <b>{@link #cancelByAdmin} 의 경고가 가리키던 그 호출부가 실제로 생긴 것</b>이다 —
     * *«한쪽만 부른 호출부가 생기면 거짓말을 한다»*. 그래서 여기서도 <b>한 메서드 안에서</b> 함께 정한다.
     */
    public void cancelByAdminFromItems(UUID adminId, String adminName) {
        cancel(null);
        this.cancelledBy = adminId;
        this.cancelledByName = adminName;
    }

    /**
     * 배송완료 주문만, 그리고 <b>기한 안에서만</b> 반품 요청할 수 있다
     * (운송 중·미결제 주문은 취소로 처리).
     *
     * <p>🔴 <b>인자를 받는 것이 일부러다</b> (2026-08-27, §I-9). 무인자 판이 남아 있으면
     * <b>기한을 안 보는 질문</b>을 누구나 부를 수 있고, 그게 WA §1-2-1 이 말하는 «짝 중 한쪽» 이 된다.
     * 시그니처를 바꿔 <b>컴파일러가 모든 호출자를 열게</b> 했다.
     * ⚠ {@code now} 도 인자다 — {@code Instant.now()} 를 안에서 부르면 <b>경계를 테스트할 수 없다.</b>
     *
     * <p>🔴 <b>남은 것이 있어야 한다</b> (2026-08-25, G-10). 부분 반품 승인은 주문을 {@code DELIVERED}
     * 로 되돌리므로 <b>상태만 보면 이미 다 반품된 주문도 다시 요청할 수 있는 것처럼 보인다.</b>
     * 실제로는 {@code hasNothingLeft()} 면 {@code RETURNED} 로 떨어져 여기 안 오지만, 이 가드가
     * 상태와 수량 <b>둘 다</b>를 보게 해서 그 둘이 어긋나도 조용히 통과하지 않는다.
     */
    public boolean isReturnRequestable(int returnGraceDays, Instant now) {
        return status == OrderStatus.DELIVERED && !hasNothingLeft()
                && isWithinReturnWindow(returnGraceDays, now);
    }

    /**
     * 반품 요청 마감 시각 = <b>배송완료 시각 + 유예일</b>. 배송 전이면 {@code null}.
     *
     * <p>🔴 <b>«최초» 배송완료다</b> (2026-08-27, §I-9 결정 2). 부분 반품 승인과 거절은 주문을
     * {@code DELIVERED} 로 되돌리지만 <b>{@code deliveredAt} 은 안 건드린다</b> — 갱신하면 한 개씩
     * 나눠 요청하는 것만으로 기한이 무한히 늘어 <b>기한이 없는 것과 거의 같아진다.</b>
     * ⚠ 그 성질은 {@code OrderPartialReturnTest} 가 지킨다 — 여기서 다시 보장하지 않는다.
     *
     * <p>⚠ <b>화면에도 이 값을 그대로 내려준다</b>({@code OrderResponse.returnDeadline}) —
     * «언제까지인가» 를 화면이 계산하면 서버와 어긋난다.
     */
    public Instant returnDeadline(int returnGraceDays) {
        return deliveredAt == null ? null : deliveredAt.plus(Duration.ofDays(returnGraceDays));
    }

    /**
     * 지금이 반품 요청 기간 안인가.
     *
     * <p>⚠ <b>경계는 «마감 시각 이전» 이다</b> — 정확히 마감 시각이면 닫힌 것으로 본다.
     * 하루 단위 정책에 초 단위 경계가 생기는 것이 어색해 보이지만, 기준이 «배송완료 시각» 이라
     * 애초에 시각 단위다({@code catalog.purge-grace-days} 와 같은 모양).
     */
    public boolean isWithinReturnWindow(int returnGraceDays, Instant now) {
        Instant deadline = returnDeadline(returnGraceDays);
        return deadline != null && now.isBefore(deadline);
    }

    /** 요청된 반품만 승인·거절할 수 있다. */
    public boolean isReturnPending() {
        return status == OrderStatus.RETURN_REQUESTED;
    }

    /**
     * 고객의 반품 요청.
     *
     * <p>⚠ <b>거절당한 뒤 다시 요청할 수 있다</b> — 거절은 {@code DELIVERED} 로 되돌리므로
     * {@link #isReturnRequestable(int, Instant)} 이 (기한 안이라면) 다시 참이 된다. 그때 <b>이전 거절 기록을 지운다</b>(2026-08-11):
     * 안 지우면 화면에 «요청됨» 과 «거절됨» 이 <b>동시에</b> 뜨고, 어느 쪽이 지금인지 알 수 없다.
     * ⚠ 지우는 것이 이력 손실이긴 하다. 주문 한 건이 반품 사이클을 <b>하나만</b> 들고 있는 구조라
     * ({@code returnReason} 도 덮어쓴다) 여기서만 특별히 쌓아 둘 수 없다 —
     * 이력이 필요해지면 <b>별도 테이블</b>이 답이지 컬럼을 늘리는 게 아니다.
     */
    public void requestReturn(String reason, Map<UUID, Long> quantitiesByItemId) {
        // ⚠ **고르지 않은 품목은 0 으로 덮는다**(누적이 아니다). 거절 뒤 다시 요청할 때 이전 회차의
        //    수량이 남아 있으면 «안 고른 품목이 따라 반품되는» 일이 생긴다.
        for (OrderItem item : items) {
            item.requestReturn(quantitiesByItemId.getOrDefault(item.getId(), 0L));
        }
        this.status = OrderStatus.RETURN_REQUESTED;
        this.returnReason = reason;
        this.returnRequestedAt = Instant.now();
        this.returnRejectedReason = null;
        this.returnRejectedAt = null;
    }

    /**
     * 🔴 <b>요청된 반품을 확정하고 정산을 나눈다</b> (2026-08-25, BACKLOG G-10).
     *
     * <p><b>배분식</b> — 규칙 원본은 BACKLOG G-10 이다. {@link #cancelItem} 과 <b>같은 모양</b>이고
     * 분모·분자가 «지금 남은 값» 인 것도 같다(그래서 전액 수렴이 구조로 보장된다):
     * <pre>
     *   반품금액   = 단가 × 확정수량
     *   쿠폰 몫    = 남은쿠폰할인 × 반품금액 / 남은상품합계                      (내림)
     *   적립금 몫  = 남은적립금   × 반품금액 / 남은상품합계                      (내림)
     *   환불액     = 반품금액 − 쿠폰 몫                                          ← 🔴 취소와 다르다
     *   적립 회수  = 남은적립 × (반품금액 − 쿠폰몫 − 적립금몫) / 남은적립기준액  (내림)
     *   등급 차감  = 반품금액 − 쿠폰 몫 − 적립금 몫
     * </pre>
     *
     * <p>🔴 <b>환불액에 «적립금 몫» 이 들어 있다.</b> 반품은 «결제금액을 적립금으로» 돌려주는 것이라
     * 현금결제분과 사용적립금을 <b>함께</b> 적립금으로 준다({@code PointService.refundReturnedOrder}).
     * ⚠ 그래서 {@code pointShare} 는 환불 계산에 안 쓰이지만 <b>여전히 누적한다</b> —
     * {@link #remainingUsedPoint()} 를 낮춰야 다음 회차의 분모와 {@link #rewardableAmount()} 가 맞는다.
     * <b>취소({@code cancelItem})와 헷갈리기 가장 쉬운 자리다.</b>
     *
     * <p>✅ <b>수렴</b>: 전량을 부분 반품으로 다 빼면 Σ환불액 = {@code refundableAmount()} 처음 값,
     * Σ적립회수 = {@code earnedPoint}, Σ등급차감 = {@code rewardableAmount()} 처음 값이 된다 —
     * 즉 <b>지금의 전체 반품과 글자 그대로 같은 값</b>이다. 그게 이 설계의 안전 조건이다.
     */
    public ReturnSettlement applyRequestedReturns() {
        return settleRequestedReturns(true);
    }

    /**
     * 지금 요청된 반품을 <b>승인하면 얼마인가</b> — 아무것도 바꾸지 않는다 (G-10).
     *
     * <p>🔴 <b>«누르기 전에 보여주려고» 있다.</b> 08-24 가 부분 취소에서 같은 것을 화면에 두면서
     * 배분식이 두 벌이 됐는데, 여기서는 <b>서버가 한 벌만 갖는다</b> — {@link #applyRequestedReturns()}
     * 와 <b>글자 그대로 같은 코드</b>를 타고 마지막에 쓰기만 안 한다. 어긋날 자리가 없다.
     */
    public ReturnSettlement previewRequestedReturns() {
        return settleRequestedReturns(false);
    }

    /**
     * 🔴 <b>배분 본체 — «미리 보기» 와 «확정» 이 같은 코드를 탄다.</b>
     *
     * <p>⚠ 회차 안에서 품목을 여러 개 처리하므로 <b>«이번 회차에 이미 뗀 몫»({@code d*})을 빼 가며</b>
     * 분모·분자를 다시 만든다. 필드를 바로 더해 버리면 미리 보기가 상태를 오염시키고, 반대로
     * 루프 밖에서 한 번만 잡으면 두 번째 품목부터 «이미 빠진 것» 을 분모에 넣는다.
     * <b>내림 배분이 경로 의존인 이유이자, 마지막 품목이 잔돈을 전부 흡수하는 이유다.</b>
     *
     * @param apply {@code false} 면 계산만 한다 — 엔티티도 품목도 안 건드린다
     */
    private ReturnSettlement settleRequestedReturns(boolean apply) {
        long dItems = 0L;
        long dCoupon = 0L;
        long dPoint = 0L;
        long dEarn = 0L;
        long refund = 0L;
        long purchaseToRemove = 0L;

        for (OrderItem item : items) {
            long qty = item.getReturnRequestedQuantity();
            if (qty <= 0) {
                continue;
            }
            long base = remainingItemsTotal() - dItems;
            if (base <= 0) {
                throw new IllegalStateException("남은 품목이 없는 주문에서 반품을 확정하려 했다: " + orderNo);
            }
            long remCoupon = remainingCouponDiscount() - dCoupon;
            long remPoint = remainingUsedPoint() - dPoint;
            long remEarn = remainingEarnedPoint() - dEarn;
            // 적립 기준액 = 남은상품합계 − 남은쿠폰 − 남은적립금 (= 이 시점의 rewardableAmount()).
            long rewardBase = base - remCoupon - remPoint;

            long amount = item.getPrice() * qty;
            long couponShare = remCoupon * amount / base;
            long pointShare = remPoint * amount / base;
            // 🔴 취소와 **같은 규칙**이다(§K-3) — 회차가 갈려도 부등식이 유지되어야
            //    `share`(누적구매 차감)가 음수로 안 내려간다.
            pointShare += residueIntoPoint(remCoupon, remPoint, couponShare, pointShare, base, amount);
            long share = amount - couponShare - pointShare;
            // ⚠ 적립 기준액이 0 인 주문이 있다(쿠폰·적립금으로 전액을 낸 경우). 그때는 적립도 0 이라
            //    회수할 것이 없다 — 0 나눗셈을 막는 동시에 «없는 것을 뺀다» 도 막는다.
            long earnShare = rewardBase <= 0 ? 0L : remEarn * share / rewardBase;

            dItems += amount;
            dCoupon += couponShare;
            dPoint += pointShare;
            dEarn += earnShare;
            refund += amount - couponShare;
            purchaseToRemove += share;
        }

        if (apply) {
            items.forEach(OrderItem::confirmReturn);
            this.returnedItemsTotal += dItems;
            this.returnedCouponDiscount += dCoupon;
            this.returnedPoint += dPoint;
            this.reversedEarnedPoint += dEarn;
            this.returnedAt = Instant.now();
            // 🔴 남은 것이 있으면 **배송완료로 되돌린다** — 다시 반품을 요청할 수 있어야 한다.
            //    「품목이 다 빠졌는데 상태는 DELIVERED」인 주문을 만들지 않는다(G-4 가 PAID 에서 정한 것과 같다).
            this.status = hasNothingLeft() ? OrderStatus.RETURNED : OrderStatus.DELIVERED;
        }
        return new ReturnSettlement(refund, dEarn, purchaseToRemove);
    }

    /**
     * 관리자 거절 — 배송완료 상태로 되돌리되 <b>있었던 일은 전부 남긴다</b>.
     *
     * <p>🔴 <b>2026-08-11 정정</b>: 예전 주석은 *"사유는 기록으로 남겨 둔다(요청이 있었다는 흔적)"*
     * 였는데 <b>동작이 그 의도를 배신했다</b> — {@code returnRequestedAt} 을 NULL 로 지웠고,
     * 화면의 반품 카드는 {@code DELIVERED} 를 렌더 조건에서 빼 놓아 <b>카드째 사라졌다.</b>
     * 결과적으로 «반품을 요청한 적 없는 배송완료 주문» 과 구분이 안 됐다.
     * → <b>요청 시각을 지우지 않는다.</b> 요청 시각 + 거절 시각이 함께 있어야
     * «언제 요청해서 언제 거절됐나» 가 읽힌다.
     *
     * <p>⚠ 재고·적립금·쿠폰은 <b>건드리지 않는다</b> — 승인하지 않았으므로 되돌릴 것이 없다.
     */
    public void rejectReturn(String reason) {
        // 🔴 **요청 수량도 지운다** (2026-08-25, G-10). 안 지우면 다음에 다른 품목을 요청했을 때
        //    이전 회차의 수량이 남아 **안 고른 품목이 따라 반품된다.** 승인을 안 했으니 되돌릴 것은
        //    이것뿐이다 — 수량·돈은 아직 안 움직였다.
        items.forEach(OrderItem::clearReturnRequest);
        this.status = OrderStatus.DELIVERED;
        this.returnRejectedReason = reason;
        this.returnRejectedAt = Instant.now();
    }

    /**
     * <b>지금 전량을 반품하면</b> 돌려줄 금액 = 남은 상품합계 − 남은 쿠폰할인. 사용했던 적립금 +
     * 현금분을 한꺼번에 적립금으로 돌려준다. 배송비는 뺀다(운임은 소진됐다 — G-10 결정 3).
     *
     * <p>⚠ <b>실제 환불은 이 값이 아니라 {@link #applyRequestedReturns()} 가 낸다</b>(2026-08-25, G-10) —
     * 부분 반품은 요청된 품목의 몫만 돌려주기 때문이다. 이 메서드는 이제 <b>화면이 «전량 반품하면
     * 얼마»를 미리 보여주는</b> 용도이고, 전량을 나눠 반품하면 그 합이 정확히 이 값이 된다(수렴).
     */
    public long refundableAmount() {
        // ⚠ **남은 것 기준**(2026-08-24, G-4). 부분 취소로 이미 돌려준 몫을 반품에서 또 돌려주면
        //   환불이 결제금액보다 커진다. 부분 취소가 없으면 예전과 같은 값이다.
        return Math.max(0L, remainingItemsTotal() - remainingCouponDiscount());
    }

    public boolean isOwnedBy(UUID memberId) {
        return this.memberId.equals(memberId);
    }
}
