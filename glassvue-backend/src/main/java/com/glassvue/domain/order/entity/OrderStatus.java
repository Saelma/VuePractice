package com.glassvue.domain.order.entity;

/**
 * 주문 상태 흐름: ORDERED → PAID → SHIPPED (정상), 각 단계에서 CANCELLED 가능(SHIPPED 제외).
 * 결제(PAID) 전이는 지금은 상태 플래그만 — 실제 결제는 이후 PG 연동으로 대체(현재 플레이스홀더).
 */
public enum OrderStatus {
    ORDERED,   // 주문됨(결제 대기)
    PAID,      // 결제 완료
    SHIPPED,   // 발송 완료
    CANCELLED  // 취소됨
}
