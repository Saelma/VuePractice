package com.glassvue.global.security;

import com.glassvue.domain.member.entity.Role;
import java.util.UUID;

/** 인증된 사용자 principal. 컨트롤러에서 @AuthenticationPrincipal AuthUser 로 받는다. */
public record AuthUser(UUID id, Role role) {
}
