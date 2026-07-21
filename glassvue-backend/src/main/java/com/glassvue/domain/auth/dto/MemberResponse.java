package com.glassvue.domain.auth.dto;

import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import java.util.UUID;

/** ship* 필드는 **기본 배송지**(주문서 자동 채움용). 설정 전이면 전부 null이다. */
public record MemberResponse(UUID id, String loginId, String nickname, Role role,
                             String shipRecipient, String shipPhone, String shipZipcode,
                             String shipAddress1, String shipAddress2) {

    public static MemberResponse from(Member m) {
        return new MemberResponse(m.getId(), m.getLoginId(), m.getNickname(), m.getRole(),
                m.getShipRecipient(), m.getShipPhone(), m.getShipZipcode(),
                m.getShipAddress1(), m.getShipAddress2());
    }
}
