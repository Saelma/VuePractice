package com.glassvue.domain.auth.dto;

import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import java.util.UUID;

public record MemberResponse(UUID id, String loginId, String nickname, Role role) {

    public static MemberResponse from(Member m) {
        return new MemberResponse(m.getId(), m.getLoginId(), m.getNickname(), m.getRole());
    }
}
