package com.glassvue.domain.audit.entity;

/**
 * 감사 대상 관리자 조작의 종류. 지금은 회원 조작(정지·해제·역할변경)만이지만, 앞으로 주문·상품 등
 * 다른 도메인의 관리자 조작도 같은 테이블에 남길 수 있게 값으로만 구분한다.
 *
 * <p>DB 에는 문자열로 저장(CHECK 제약, {@code V32}). 값을 추가할 때는 CHECK 제약도 함께 넓혀야 한다
 * (Oracle enum CHECK 트랩 — 메모리 참조).
 */
public enum AuditAction {
    /** 회원 정지. */
    MEMBER_SUSPEND,
    /** 회원 정지 해제. */
    MEMBER_UNSUSPEND,
    /** 회원 역할 변경(USER↔ADMIN). */
    MEMBER_ROLE_CHANGE,
    /**
     * 회원 강제 삭제(B-24, 2026-07-30). 되돌릴 수 없는 조작이라 <b>SUPER_ADMIN 전용</b>이다.
     *
     * <p>⚠ 이 값을 더할 때 {@code V35} 로 CHECK 제약을 함께 넓혔다 — {@code ddl-auto=update} 도 아니고
     * {@code validate} 라 제약은 절대 자동으로 안 따라온다(Oracle enum CHECK 트랩).
     */
    MEMBER_DELETE,
    /**
     * 관리자 대행 주문 취소(B-25, 2026-08-10). <b>회원이 아닌 것을 대상으로 하는 첫 값</b>이다 —
     * 정확히는 대상을 <b>주문자(회원)</b> 로 잡고 주문번호는 {@code detail} 에 넣는다.
     *
     * <p>⚠ 이 자리는 B-18(리뷰 숨김)에서 <b>감사를 붙이지 못했던</b> 그 자리다. 그때 막힌 이유는
     * *"{@code targetId} 가 대상 회원인데 리뷰는 회원이 아니다"* 였는데, <b>주문에는 주문자가 있어</b>
     * 같은 문제가 생기지 않는다. 즉 «감사가 회원 대상 설계라 못 쓴다» 는 도메인이 아니라
     * <b>대상에 회원이 있느냐</b>로 갈린다.
     *
     * <p>⚠ {@code V43} 으로 CHECK 제약을 함께 넓혔다 — {@code ddl-auto=validate} 라 절대 자동으로
     * 안 따라온다(Oracle enum CHECK 트랩). 넓히는 방향이라 구 jar 는 영향받지 않는다.
     */
    ORDER_CANCEL
}
