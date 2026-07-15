package com.glassvue.domain.member.entity;

public enum Role {
    USER,
    ADMIN;

    /** Spring Security 권한 이름 (ROLE_ 접두사) */
    public String authority() {
        return "ROLE_" + name();
    }
}
