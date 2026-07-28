package com.glassvue.domain.member.entity;

import java.util.List;

public enum Role {
    USER,
    ADMIN,
    // 최상위 관리자(2026-07-28). 관리자 계정의 정지·역할변경은 이 권한만 할 수 있고, 이 계정은
    // 아무도 정지·강등하지 못한다. 인가상 ADMIN 을 포함한다(authorities 참조).
    SUPER_ADMIN;

    /** Spring Security 권한 이름 (ROLE_ 접두사) */
    public String authority() {
        return "ROLE_" + name();
    }

    /**
     * 인가에 쓰는 권한 목록. SUPER_ADMIN 은 <b>ADMIN 을 포함</b>한다 — 기존 {@code /api/admin/**}
     * (hasRole('ADMIN'))를 그대로 통과해야 하고, SUPER_ADMIN 전용 판단은 서비스 계층에서 한다.
     */
    public List<String> authorities() {
        if (this == SUPER_ADMIN) {
            return List.of(authority(), ADMIN.authority());
        }
        return List.of(authority());
    }
}
