package com.glassvue.global.security;

import com.glassvue.domain.member.entity.Role;
import java.util.UUID;

/** 인증된 사용자 principal. 컨트롤러에서 @AuthenticationPrincipal AuthUser 로 받는다. */
public record AuthUser(UUID id, Role role, String nickname) {

    /**
     * 이 사용자가 <b>관리자 이상</b>인가 — 서비스 계층의 «관리자면 남의 것도 다룬다» 판단은 여기로 모은다.
     * 판정 자체는 {@link Role#isAdmin()} 이 갖고, 여기는 {@code user.role() == ...} 을 쓸 일을 없애는 짝이다.
     */
    public boolean isAdmin() {
        return role != null && role.isAdmin();
    }
}
