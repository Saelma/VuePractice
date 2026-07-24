package com.glassvue.domain.member.repository;

import com.glassvue.domain.member.entity.MemberAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberAddressRepository extends JpaRepository<MemberAddress, UUID> {

    /** 내 주소록 — 기본 배송지가 먼저, 그다음 등록 순. V18 의 (member_id, is_default) 인덱스를 탄다. */
    List<MemberAddress> findByMemberIdOrderByIsDefaultDescCreatedAtAsc(UUID memberId);

    Optional<MemberAddress> findByMemberIdAndIsDefaultTrue(UUID memberId);

    /**
     * 소유 확인을 조회 조건에 넣는다 — 남의 주소는 <b>없는 것으로</b> 답한다(존재 여부를 알려주지 않는다).
     * 쿠폰에서 남의 쿠폰을 COUPON_NOT_FOUND 로 답한 것과 같은 판단이다.
     */
    Optional<MemberAddress> findByIdAndMemberId(UUID id, UUID memberId);

    /** 기본 배송지를 지운 뒤 승계할 대상 — 가장 먼저 등록한 주소. */
    Optional<MemberAddress> findFirstByMemberIdOrderByCreatedAtAsc(UUID memberId);

    long countByMemberId(UUID memberId);
}
