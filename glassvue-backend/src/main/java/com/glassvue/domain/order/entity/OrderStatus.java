package com.glassvue.domain.order.entity;

/**
 * 주문 상태 흐름: ORDERED → PAID → SHIPPED → DELIVERED (정상), 각 단계에서 CANCELLED 가능(발송 이후 제외).
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
    CANCELLED  // 취소됨
}
