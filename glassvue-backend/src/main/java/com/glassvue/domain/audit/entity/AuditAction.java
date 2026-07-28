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
    MEMBER_ROLE_CHANGE
}
