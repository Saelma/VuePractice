package com.glassvue.domain.point.entity;

/**
 * 회원 등급 (2026-07-24, 백로그 C-10).
 *
 * <p>기준은 <b>누적 구매확정액</b>(배송완료된 주문의 상품매출 합)이고, 효과는 <b>적립률</b>이다.
 * 등급을 올리는 것과 적립을 주는 것이 <b>같은 시점·같은 금액 기준</b>이라 규칙이 하나로 유지된다.
 *
 * <p>임계값·적립률을 설정(application.yml)으로 빼지 않은 이유: 지금은 <b>바뀔 이유가 없고</b>,
 * 설정으로 빼면 "지금 몇 %인지"를 코드에서 읽을 수 없어 오히려 추적이 어려워진다.
 * 등급이 실제로 운영 정책이 되면 그때 옮긴다(배송비 정책이 설정인 것과는 성격이 다르다 —
 * 그건 처음부터 "정책"이었다).
 */
public enum MemberGrade {

    BRONZE(0L, 1),
    SILVER(100_000L, 2),
    GOLD(500_000L, 3),
    VIP(1_000_000L, 5);

    private final long minPurchase;
    private final int earnPercent;

    MemberGrade(long minPurchase, int earnPercent) {
        this.minPurchase = minPurchase;
        this.earnPercent = earnPercent;
    }

    public long minPurchase() {
        return minPurchase;
    }

    public int earnPercent() {
        return earnPercent;
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
}
