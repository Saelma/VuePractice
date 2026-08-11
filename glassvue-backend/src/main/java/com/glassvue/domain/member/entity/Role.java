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

    /**
     * <b>"관리자 이상인가"</b> — 서비스 계층의 «ADMIN 이면 남의 것도 다룬다» 판단은 전부 여기를 쓴다.
     * 프론트 {@code isAdminRole}(stores/auth.js)의 백엔드 짝이다.
     *
     * <p>⚠ <b>{@code == Role.ADMIN} 으로 쓰면 SUPER_ADMIN 이 떨어진다.</b> JWT 에는 {@code role.name()}
     * 하나만 담기므로 {@code AuthUser.role()} 은 문자 그대로 {@code SUPER_ADMIN} 이고,
     * {@link #authorities()} 가 통과시키는 것은 <b>URL 인가까지</b>다 — 서비스 계층의 enum 동일성 비교는
     * 그 포함관계를 모른다. 그래서 «버튼은 보이는데 누르면 404/403» 이 된다(2026-08-10 §16-3 실측:
     * 여섯 자리에서 실제 운영 계정이 떨어져 있었고, 프론트는 2026-07-28 에 이미 이쪽으로 옮겨져 있었다).
     *
     * <p>⚠ <b>대상(target)의 역할을 보는 자리에는 쓰지 않는다.</b> {@code MemberAdminCommandService} 의
     * {@code target.getRole() == Role.ADMIN} 은 «대상이 ADMIN 이면 SUPER 만 건드린다» 라 <b>의도대로</b>고,
     * 여기로 바꾸면 규칙이 뒤집힌다. 이 메서드는 <b>행위자(actor)</b> 판정 전용이다.
     */
    public boolean isAdmin() {
        return this == ADMIN || this == SUPER_ADMIN;
    }

    /** {@link #isAdmin()} 과 같은 경계를 <b>쿼리로</b> 물어야 할 때 (예: 관리자 전원에게 알림). */
    public static List<Role> adminRoles() {
        return List.of(ADMIN, SUPER_ADMIN);
    }
}
