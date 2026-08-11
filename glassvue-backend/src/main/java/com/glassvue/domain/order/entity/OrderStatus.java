package com.glassvue.domain.order.entity;

/**
 * 주문 상태 흐름: ORDERED → PAID → SHIPPED → DELIVERED (정상), 각 단계에서 CANCELLED 가능(발송 이후 제외).
 * 배송완료 후: DELIVERED → RETURN_REQUESTED → RETURNED (반품 승인) 또는 → DELIVERED (반품 거절).
 * 결제(PAID) 전이는 지금은 상태 플래그만 — 실제 결제는 이후 PG 연동으로 대체(현재 플레이스홀더).
 *
 * <p>⚠ 값을 추가하면 {@code orders.status}의 CHECK 제약도 함께 고쳐야 한다 — enum만 늘리면
 * 저장할 때 ORA-02290으로 터진다. 제약 이름은 V13에서 {@code ck_orders_status}로 붙여 뒀으므로
 * 다음부터는 DROP/ADD 두 줄이면 된다(V13 이전에는 자동 생성 이름이라 동적으로 찾아야 했다).
 */
public enum OrderStatus {
    ORDERED,   // 주문됨(결제 대기)
    PAID,      // 결제 완료
    SHIPPED,   // 발송 완료(운송장 등록됨)
    DELIVERED, // 배송 완료(수령)
    CANCELLED, // 취소됨(ORDERED·PAID 에서만)
    RETURN_REQUESTED, // 반품 요청됨(DELIVERED 에서만) — 관리자 승인 대기
    RETURNED;  // 반품 완료(승인) — 재고 복원 + 적립금 환불됨

    /**
     * 이 상태가 <b>「이 사람은 그 상품을 샀다」를 증명하는가</b> — 리뷰 구매 인증의 기준
     * ({@code OrderRepository.existsPurchase}).
     *
     * <p>⚠ <b>이 자리는 두 번 어긋났다.</b> 2026-07-20 에 {@code ORDERED} 만 세던 것을
     * {@code ORDERED·PAID·SHIPPED} 로 고쳤고(그때는 그게 «CANCELLED만 제외» 였다), 2026-07-23(V13)에
     * {@code DELIVERED} 를 <b>여기 이 enum 에 추가하면서</b> 리포지토리 쪽 열거는 안 늘어나
     * <b>배송완료 고객이 리뷰를 못 쓰는 상태가 됐다</b>(2026-08-10 §16-2 에서 발견, 운영 3조합이 막혀 있었다).
     *
     * <p>→ 그래서 판정을 <b>상태를 새로 만드는 사람의 눈앞</b>으로 옮겼다. 아래 switch 에는
     * <b>{@code default} 가 없다</b> — 값을 추가하면 «망라하지 않는다» 로 <b>컴파일이 깨진다.</b>
     * 리포지토리 주석에 «새 상태는 직접 추가할 것» 이라고 적어 두는 방식은 두 번 다 안 지켜졌다.
     * <b>규율로 못 지키는 것은 컴파일러에게 맡긴다.</b>
     *
     * <p>{@code RETURN_REQUESTED}·{@code RETURNED} 를 <b>제외</b>한 것은 제품 판단이다(2026-08-11, 사용자 결정):
     * 둘 다 물건은 받아 봤지만 {@code RETURNED} 는 <b>돈을 돌려준</b> 상태라 «샀다가 물린 사람» 이고,
     * 그 리뷰를 구매 후기로 세지 않기로 했다. 넓히려면 <b>여기 한 줄</b>만 바꾸면 된다.
     */
    public boolean isPurchaseProven() {
        return switch (this) {
            case ORDERED, PAID, SHIPPED, DELIVERED -> true;
            case CANCELLED, RETURN_REQUESTED, RETURNED -> false;
        };
    }

    /** {@link #isPurchaseProven()} 인 상태 전부 — 쿼리 파라미터로 넘길 때 쓴다. */
    public static java.util.Set<OrderStatus> purchaseProven() {
        return java.util.EnumSet.allOf(OrderStatus.class).stream()
                .filter(OrderStatus::isPurchaseProven)
                .collect(java.util.stream.Collectors.toCollection(() -> java.util.EnumSet.noneOf(OrderStatus.class)));
    }
}
