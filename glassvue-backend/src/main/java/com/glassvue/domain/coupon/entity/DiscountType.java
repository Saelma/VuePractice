package com.glassvue.domain.coupon.entity;

/**
 * 할인 방식.
 *
 * <p>⚠ 값을 추가하면 {@code coupon.discount_type} 의 CHECK 제약도 함께 고쳐야 한다.
 * 제약에 이름을 붙여 뒀으므로({@code ck_coupon_discount_type}) DROP/ADD 두 줄이면 된다 —
 * V1 이 인라인으로 선언한 {@code orders.status} 를 V13 에서 고칠 때 자동 생성 이름을 찾아
 * 지워야 했던 걸 되풀이하지 않으려는 것이다.
 */
public enum DiscountType {
    /** 정액 할인(원). */
    FIXED,
    /** 정률 할인(%). {@code maxDiscountAmount} 로 상한을 둘 수 있다. */
    PERCENT
}
