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

    /**
     * 이 상태가 <b>매출로 잡히는가</b> — 관리자 매출 통계의 기준({@code OrderStatsQueryService}).
     *
     * <p>⚠ <b>{@link #isPurchaseProven()} 과 다르다.</b> 저건 «이 사람이 샀나»(리뷰 자격)이고
     * 이건 «돈이 우리 것인가»다. 갈리는 자리가 실제로 있다:
     * <ul>
     *   <li>{@code ORDERED} — 샀다고 볼 수는 있지만 <b>결제 전</b>이라 매출이 아니다.</li>
     *   <li>{@code RETURNED} — 물건은 받아 봤지만 <b>돈을 돌려줬으니</b> 매출이 아니다.</li>
     * </ul>
     * 그래서 둘을 한 메서드로 합치지 않는다 — 합치면 한쪽 정책을 바꿀 때 다른 쪽이 조용히 따라간다.
     *
     * <p>🔴 <b>{@code RETURN_REQUESTED} 는 매출이다</b>(2026-08-11 결정). 반품은 <b>승인되어야 확정</b>이고,
     * 요청은 고객의 의사표시일 뿐이다. 요청만으로 빼면 두 가지가 어긋난다:
     * <ul>
     *   <li><b>판매량과 시점이 달라진다</b> — {@code sold_count} 는 승인(RETURNED)에만 반응한다
     *       ({@code SalesEventListener}). 요청 상태에서 매출은 빠졌는데 판매량은 남는다.</li>
     *   <li>🔴 <b>과거 날짜의 매출이 나중에 바뀐다</b> — 거절하면 {@code DELIVERED} 로 돌아가
     *       그 금액이 <b>다시 잡힌다.</b> 어제 본 일별 매출과 오늘 본 것이 달라진다.</li>
     * </ul>
     * 실측(2026-08-11, 고치기 전): 매출 347,000원(14건) ↔ 요청 포함 355,000원(15건) — <b>8,000원이
     * 승인도 거절도 안 된 채 빠져 있었다</b>(08-10 §16-4 8번).
     *
     * <p>⚠ <b>{@code default} 가 없다</b> — 상태를 추가하면 컴파일이 깨진다({@link #isPurchaseProven()} 과
     * 같은 장치). 「새 상태는 여기 직접 넣도록 opt-in 으로 둔다」를 주석으로만 적어 두면 안 지켜진다는 것을
     * 오늘 아침에 이미 겪었다(§3-1).
     */
    public boolean isRevenue() {
        return switch (this) {
            case PAID, SHIPPED, DELIVERED, RETURN_REQUESTED -> true;
            case ORDERED, CANCELLED, RETURNED -> false;
        };
    }

    /** {@link #isRevenue()} 인 상태 이름 전부 — 통계 쿼리가 문자열로 받는다. */
    public static java.util.List<String> revenueStatusNames() {
        return java.util.Arrays.stream(values())
                .filter(OrderStatus::isRevenue)
                .map(Enum::name)
                .toList();
    }

    /** {@link #isPurchaseProven()} 인 상태 전부 — 쿼리 파라미터로 넘길 때 쓴다. */
    public static java.util.Set<OrderStatus> purchaseProven() {
        return java.util.EnumSet.allOf(OrderStatus.class).stream()
                .filter(OrderStatus::isPurchaseProven)
                .collect(java.util.stream.Collectors.toCollection(() -> java.util.EnumSet.noneOf(OrderStatus.class)));
    }
}
