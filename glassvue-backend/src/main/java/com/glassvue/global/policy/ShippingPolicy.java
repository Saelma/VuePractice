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
     * 상품 합계로 배송비를 계산한다.
     *
     * <p>합계가 0 이하면 0 — 빈 장바구니에 배송비를 붙이면 화면에 "0원 + 배송비 3,000원"이 떠서
     * 담지도 않은 값을 청구하는 것처럼 보인다.
     */
    public long feeFor(long itemsTotal) {
        if (itemsTotal <= 0) {
            return 0;
        }
        if (freeThreshold > 0 && itemsTotal >= freeThreshold) {
            return 0;
        }
        return fee;
    }

    /** 무료배송까지 남은 금액. 이미 무료이거나 무료배송 정책이 없으면 0. */
    public long amountUntilFree(long itemsTotal) {
        if (freeThreshold <= 0 || itemsTotal <= 0 || itemsTotal >= freeThreshold) {
            return 0;
        }
        return freeThreshold - itemsTotal;
    }
}
