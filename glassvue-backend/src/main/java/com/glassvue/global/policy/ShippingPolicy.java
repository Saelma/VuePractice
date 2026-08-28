package com.glassvue.global.policy;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 배송비 정책(설정 {@code glassvue.shipping}).
 *
 * <p><b>왜 도메인이 아니라 global 인가</b> — 배송비는 <b>장바구니</b>(주문 전 미리보기)와
 * <b>주문</b>(실제 부과) 양쪽이 읽어야 한다. 그런데 이미 {@code order → cart} 의존이 있어서
 * (주문이 장바구니에서 품목을 읽는다) 정책을 order 에 두면 {@code cart → order} 가 생겨 <b>순환</b>이 된다.
 * 여러 도메인이 공유하는 상점 전체 정책이므로 global 에 둔다 — MSA 로 쪼개도 이건 공유 설정이 된다.
 *
 * <p><b>정책은 설정, 부과된 금액은 스냅샷</b>. 정책이 바뀌어도 과거 주문의 배송비는 그대로여야 하므로
 * 주문에는 계산 결과를 저장한다({@code orders.shipping_fee}) — 배송지·구매자 닉네임과 같은 이유.
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "glassvue.shipping")
public class ShippingPolicy {

    /** 기본 배송비(원). */
    private long fee = 3000;

    /** 상품 합계가 이 금액 이상이면 무료. 0이면 무료배송 없음. */
    private long freeThreshold = 30000;

    /**
     * 상품 합계로 배송비를 계산한다 — <b>기본 기준</b>으로.
     *
     * <p>합계가 0 이하면 0 — 빈 장바구니에 배송비를 붙이면 화면에 "0원 + 배송비 3,000원"이 떠서
     * 담지도 않은 값을 청구하는 것처럼 보인다.
     */
    public long feeFor(long itemsTotal) {
        return feeFor(itemsTotal, freeThreshold);
    }

    /**
     * 상품 합계와 <b>적용할 기준 금액</b>으로 배송비를 계산한다 (2026-08-28, BACKLOG G-6).
     *
     * <p>🔴 <b>회원 등급을 받지 않는다 — 「금액」을 받는다.</b> 등급별 무료배송을 넣으면서
     * {@code MemberGrade}(point 도메인)를 인자로 받고 싶어지는데, 그러면 global 이 도메인을 알게 되어
     * <b>MSA 로 쪼갤 때 policy 가 point 를 끌고 간다</b>. 등급 → 기준 금액 변환은
     * {@code MemberGrade.discountedThreshold} 가 하고, <b>호출자가 조회해서 넘긴다</b>.
     *
     * <p>⚠ 그래서 이 메서드는 «누구의» 기준인지 모른다 — 등급이 늘어나든 혜택 규칙이 바뀌든
     * <b>여기는 안 고쳐진다.</b> 그게 이 형태를 고른 이유다.
     *
     * @param freeThresholdToApply 이 계산에 쓸 무료배송 기준. 0 이하면 무료배송 없음(항상 부과)
     */
    public long feeFor(long itemsTotal, long freeThresholdToApply) {
        if (itemsTotal <= 0) {
            return 0;
        }
        if (freeThresholdToApply > 0 && itemsTotal >= freeThresholdToApply) {
            return 0;
        }
        return fee;
    }

    /** 무료배송까지 남은 금액 — <b>기본 기준</b>으로. 이미 무료이거나 무료배송 정책이 없으면 0. */
    public long amountUntilFree(long itemsTotal) {
        return amountUntilFree(itemsTotal, freeThreshold);
    }

    /**
     * 무료배송까지 남은 금액 — <b>적용할 기준 금액</b>으로 (2026-08-28, BACKLOG G-6).
     *
     * <p>🔴 <b>{@code feeFor} 와 반드시 같은 기준으로 불러야 한다.</b> 한쪽만 등급 기준을 쓰면
     * 화면이 «12,000원 더 담으면 무료배송» 이라 말해 놓고 실제로는 <b>이미 무료</b>인
     * 상태가 된다 — 백로그 G-6 은 {@code feeFor} 만 말하고 이 메서드를 안 적었다.
     */
    public long amountUntilFree(long itemsTotal, long freeThresholdToApply) {
        if (freeThresholdToApply <= 0 || itemsTotal <= 0 || itemsTotal >= freeThresholdToApply) {
            return 0;
        }
        return freeThresholdToApply - itemsTotal;
    }
}
