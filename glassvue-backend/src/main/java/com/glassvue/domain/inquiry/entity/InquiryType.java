package com.glassvue.domain.inquiry.entity;

/**
 * 문의 유형 (2026-08-07, G-3 2단계).
 *
 * <p><b>{@link #PRODUCT} 만 성격이 다르다</b> — 화면에서 고르는 값이 아니라 <b>작성 경로가 정한다</b>
 * ({@code POST /products/{productId}/inquiries} 로 들어오면 무조건 PRODUCT). 나머지는 일반
 * 고객센터 문의라 사용자가 고른다.
 *
 * <p>그래서 {@code productId} 와 이 값은 <b>같은 사실의 두 면</b>이다: PRODUCT ⟺ productId 가 있다.
 * 어긋난 행은 {@code Inquiry} 생성자와 DB 제약({@code ck_inquiry_product_pair})이 <b>둘 다</b> 막는다.
 *
 * <p>⚠ <b>값을 나중에 늘리지 말 것.</b> Oracle 은 {@code ddl-auto=update} 가 CHECK 제약을 못 고쳐
 * {@code ORA-02290} 이 나고 수동 {@code ALTER} 가 필요하다(이미 데인 자리). 그래서 V42 에서
 * <b>지금 화면에 안 띄우는 값까지</b> 넉넉히 잡아 뒀다 — 넣어 두는 비용은 0, 나중에 넣는 비용은 수동 ALTER 다.
 */
public enum InquiryType {

    /** 상품 문의 — 경로가 정한다. productId 가 반드시 있다. */
    PRODUCT,

    /** 배송 */
    DELIVERY,

    /** 환불·취소 */
    REFUND,

    /**
     * 결제.
     * ⚠ 지금 화면에 안 띄운다(V42 주석) — 값만 미리 열어 뒀다.
     */
    PAYMENT,

    /**
     * 회원·계정.
     * ⚠ 지금 화면에 안 띄운다(V42 주석) — 값만 미리 열어 뒀다.
     */
    ACCOUNT,

    /** 기타 */
    ETC;

    /** 상품 문의인가 — {@code productId} 가 있어야 하는 유일한 값이다. */
    public boolean requiresProduct() {
        return this == PRODUCT;
    }
}
