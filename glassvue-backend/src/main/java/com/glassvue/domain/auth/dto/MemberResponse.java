package com.glassvue.domain.auth.dto;

import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.MemberAddress;
import com.glassvue.domain.member.entity.Role;
import java.util.UUID;

/**
 * ship* 필드는 <b>기본 배송지</b>(주문서 자동 채움용). 설정 전이면 전부 null이다.
 *
 * <p>2026-07-24(V18)에 값의 <b>출처</b>가 바뀌었다 — {@code member.ship_*} 컬럼에서
 * 주소록(member_address)의 기본 항목으로. <b>응답 계약은 일부러 그대로 뒀다</b>:
 * 프론트의 주문서 자동 채움·{@code hasAddress} 판정이 이 필드를 보고 있어서, 형태를 바꾸면
 * 저장 구조 이전과 화면 개편이 한꺼번에 얽힌다. 주소록 전체는 별도 API로 조회한다.
 */
public record MemberResponse(UUID id, String loginId, String nickname, Role role,
                             String shipRecipient, String shipPhone, String shipZipcode,
                             String shipAddress1, String shipAddress2) {

    /** 기본 배송지가 없으면 {@code defaultAddress}는 null — ship* 필드가 전부 null이 된다. */
    public static MemberResponse of(Member m, MemberAddress defaultAddress) {
        if (defaultAddress == null) {
            return new MemberResponse(m.getId(), m.getLoginId(), m.getNickname(), m.getRole(),
                    null, null, null, null, null);
        }
        return new MemberResponse(m.getId(), m.getLoginId(), m.getNickname(), m.getRole(),
                defaultAddress.getRecipient(), defaultAddress.getPhone(), defaultAddress.getZipcode(),
                defaultAddress.getAddress1(), defaultAddress.getAddress2());
    }
}
