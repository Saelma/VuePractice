package com.glassvue.domain.catalog.entity;

/**
 * 재고가 변한 이유 (2026-08-04, 백로그 B-19).
 *
 * <p>부호는 여기서 유추하지 않는다 — {@link StockHistory#getQuantity()} 가 부호 있는 값이다
 * ({@code point_history.amount} 와 같은 규칙). 그래야 합계를 그냥 {@code SUM} 으로 낼 수 있고,
 * 양방향인 종류가 생겨도 규칙이 안 바뀐다.
 *
 * <p>⚠ 값을 더하려면 {@code ck_stock_history_reason} CHECK 제약을 넓히는 마이그레이션이
 * <b>따로</b> 필요하다(V35 선례). {@code ddl-auto} 는 CHECK 를 못 고쳐 ORA-02290 이 난다.
 */
public enum StockChangeReason {

    /** 주문 생성 시 차감(−). */
    ORDER,

    /** 주문 취소로 복원(+). */
    CANCEL,

    /** 반품 승인으로 복원(+). */
    RETURN,

    /**
     * 상품 등록 시 옵션의 초기 재고(+).
     *
     * <p>이걸 안 남기면 <b>원장이 성립하지 않는다</b> — 합계가 항상 초기재고만큼 모자란다.
     */
    ADMIN_CREATE,

    /** 관리자 편집(±). 옵션 추가·삭제도 여기에 들어간다. */
    ADMIN_EDIT
}
