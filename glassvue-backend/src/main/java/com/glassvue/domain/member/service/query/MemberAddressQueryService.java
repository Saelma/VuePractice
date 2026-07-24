package com.glassvue.domain.member.service.query;

import com.glassvue.domain.member.dto.MemberAddressResponse;
import com.glassvue.domain.member.entity.MemberAddress;
import com.glassvue.domain.member.repository.MemberAddressRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 배송지 주소록 조회. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberAddressQueryService {

    private final MemberAddressRepository addressRepository;

    /** 내 주소록 — 기본 배송지가 맨 위. */
    public List<MemberAddressResponse> myAddresses(UUID memberId) {
        return addressRepository.findByMemberIdOrderByIsDefaultDescCreatedAtAsc(memberId).stream()
                .map(MemberAddressResponse::from)
                .toList();
    }

    /**
     * 기본 배송지 — 없으면 {@code null}.
     *
     * <p>{@code MemberResponse.ship*} 를 채우는 데 쓴다. V18 이전에는 이 값이 {@code member.ship_*}
     * 컬럼이었고, 지금은 주소록의 기본 항목이다 — <b>응답 계약은 그대로</b>라 주문서 자동 채움은
     * 아무것도 바뀌지 않는다.
     */
    public MemberAddress findDefault(UUID memberId) {
        return addressRepository.findByMemberIdAndIsDefaultTrue(memberId).orElse(null);
    }
}
