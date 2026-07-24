package com.glassvue.domain.member.dto;

import com.glassvue.domain.member.entity.MemberAddress;
import java.util.UUID;

/** 주소록 항목 응답. {@code memberId} 는 담지 않는다 — 본인 것만 조회되므로 알려줄 이유가 없다. */
public record MemberAddressResponse(UUID id, String alias, String recipient, String phone,
                                    String zipcode, String address1, String address2,
                                    boolean isDefault) {

    public static MemberAddressResponse from(MemberAddress a) {
        return new MemberAddressResponse(a.getId(), a.getAlias(), a.getRecipient(), a.getPhone(),
                a.getZipcode(), a.getAddress1(), a.getAddress2(), a.isDefault());
    }
}
