package com.glassvue.domain.point.entity;

/**
 * 회원 등급 (2026-07-24, 백로그 C-10).
 *
 * <p>기준은 <b>누적 구매확정액</b>(배송완료된 주문의 상품매출 합)이고, 효과는 <b>적립률</b>과
 * <b>무료배송 기준 인하율</b> 둘이다(2026-08-28, BACKLOG G-6 — 그전에는 적립률 하나뿐이었다).
 * 등급을 올리는 것과 적립을 주는 것이 <b>같은 시점·같은 금액 기준</b>이라 규칙이 하나로 유지된다.
 *
 * <p>🔴 <b>둘 다 «비율» 이다 — 여기에 «원» 을 두지 않는다.</b> 무료배송 기준 금액(30,000원)은
 * {@code glassvue.shipping.free-threshold} 설정에 있고, 등급은 그 금액을 <b>몇 % 깎아 주는가</b>만
 * 안다. 금액을 여기에 적으면 «무료배송 기준» 이 설정과 enum <b>두 곳</b>에 살게 되고,
 * 한쪽만 고쳐지면 조용히 어긋난다(BACKLOG §I-1 이 사본 넷 때문에 난 사고다).
 *
 * <p>임계값·적립률을 설정(application.yml)으로 빼지 않은 이유: 지금은 <b>바뀔 이유가 없고</b>,
 * 설정으로 빼면 "지금 몇 %인지"를 코드에서 읽을 수 없어 오히려 추적이 어려워진다.
 * 등급이 실제로 운영 정책이 되면 그때 옮긴다(배송비 정책이 설정인 것과는 성격이 다르다 —
 * 그건 처음부터 "정책"이었다).
 */
public enum MemberGrade {

    BRONZE(0L, 1, 0),
    SILVER(100_000L, 2, 20),
    GOLD(500_000L, 3, 40),
    VIP(1_000_000L, 5, 60);

    private final long minPurchase;
    private final int earnPercent;
    private final int freeShippingDiscountPercent;

    MemberGrade(long minPurchase, int earnPercent, int freeShippingDiscountPercent) {
        this.minPurchase = minPurchase;
        this.earnPercent = earnPercent;
        this.freeShippingDiscountPercent = freeShippingDiscountPercent;
    }

    public long minPurchase() {
        return minPurchase;
    }

    public int earnPercent() {
        return earnPercent;
    }

    /** 무료배송 기준을 몇 % 깎아 주는가. BRONZE 는 0(=기본 기준 그대로). */
    public int freeShippingDiscountPercent() {
        return freeShippingDiscountPercent;
    }

    /**
     * 누적 구매액으로 등급을 정한다.
     *
     * <p>위에서부터 내려오며 처음 만족하는 등급을 쓴다 — 임계값 배열을 따로 두지 않아
     * <b>enum 순서와 판정 규칙이 어긋날 수 없다.</b>
     */
    public static MemberGrade of(long totalPurchase) {
        MemberGrade[] all = values();
        for (int i = all.length - 1; i >= 0; i--) {
            if (totalPurchase >= all[i].minPurchase) {
                return all[i];
            }
        }
        return BRONZE;
    }

    /**
     * 이 등급에서 결제금액에 붙는 적립금.
     *
     * <p><b>원 단위 내림</b>이다. 올림·반올림하면 1원짜리 주문에서도 적립이 나가고,
     * 그건 "적립률 1%"라는 약속과 어긋난다(고객에게 유리한 오차라도 규칙이 흐려지는 건 같다).
     */
    public long earn(long amount) {
        if (amount <= 0) {
            return 0L;
        }
        return amount * earnPercent / 100;
    }

    /**
     * 이 등급에 적용할 <b>무료배송 기준 금액</b> — 기본 기준을 인하율만큼 깎는다.
     *
     * <p>⚠ <b>이름에 «배송» 이 들어가지만 등급은 배송비를 모른다</b> — 받은 금액을 비율로 깎아
     * 돌려줄 뿐이고, 그 금액이 무엇인지는 <b>호출자가 안다</b>. 반대로 {@code ShippingPolicy}(global)는
     * 등급을 모른다 — 그래서 point 도메인과 global 사이에 화살표가 생기지 않는다
     * (BACKLOG G-6 의 ⚠ 경고: <i>"등급이 아니라 «적용할 기준 금액» 을 넘겨받는 형태여야 한다"</i>).
     *
     * <p><b>원 단위 올림이 아니라 내림</b>이다 — {@code earn} 과 같은 방향으로 맞춘다. 기준이
     * 내려가는 쪽이 고객에게 유리하므로 내림이 곧 «깎아 준 금액이 한 푼도 모자라지 않는다» 가 된다.
     *
     * <p>🔴 <b>기본 기준이 0(무료배송 없음)이면 등급도 0을 돌려준다</b> — 0의 60%는 0이라
     * 식만으로도 그렇게 되지만, «무료배송 정책이 없는데 VIP 만 무료» 같은 사고가 나지 않는다는
     * 뜻이라 명시해 둔다.
     *
     * @param baseThreshold 설정의 기본 무료배송 기준({@code ShippingPolicy.freeThreshold})
     * @return 이 등급에 적용할 기준 금액. 인하율이 0이면 {@code baseThreshold} 그대로
     */
    public long discountedThreshold(long baseThreshold) {
        if (baseThreshold <= 0 || freeShippingDiscountPercent <= 0) {
            return Math.max(0L, baseThreshold);
        }
        return baseThreshold * (100 - freeShippingDiscountPercent) / 100;
    }
}
