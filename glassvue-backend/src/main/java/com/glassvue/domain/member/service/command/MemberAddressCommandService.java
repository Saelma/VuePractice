package com.glassvue.domain.member.service.command;

import com.glassvue.domain.member.dto.MemberAddressRequest;
import com.glassvue.domain.member.dto.MemberAddressResponse;
import com.glassvue.domain.member.dto.ShippingAddressRequest;
import com.glassvue.domain.member.entity.MemberAddress;
import com.glassvue.domain.member.repository.MemberAddressRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배송지 주소록 조작.
 *
 * <p><b>기본 배송지는 회원당 하나</b>이고 DB 가 함수 기반 유니크 인덱스로 그걸 강제한다(V18).
 * 그래서 "옛 기본 해제" 와 "새 기본 지정" 이 <b>DB 에 도달하는 순서</b>가 중요하다 —
 * 이 클래스의 {@code flush()} 호출들이 전부 그 이유다. 지우면 ORA-00001 이 난다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MemberAddressCommandService {

    /** 주소록 상한. 없으면 한 계정이 무한정 쌓을 수 있고, 화면도 감당이 안 된다. */
    private static final int MAX_ADDRESSES = 10;

    private final MemberAddressRepository addressRepository;

    public MemberAddressResponse add(UUID memberId, MemberAddressRequest req) {
        long count = addressRepository.countByMemberId(memberId);
        if (count >= MAX_ADDRESSES) {
            throw new BusinessException(ErrorCode.ADDRESS_LIMIT_EXCEEDED);
        }

        MemberAddress address = MemberAddress.of(memberId, req.alias().trim(), req.recipient(),
                req.phone(), req.zipcode(), req.address1(), req.address2());

        // 첫 주소는 요청과 무관하게 기본 배송지가 된다 — 주소는 있는데 기본이 없으면
        // 주문서 자동 채움이 빈 폼이 되어 "저장해 뒀는데 왜 안 나오지" 가 된다.
        if (count == 0 || req.setDefault()) {
            clearCurrentDefault(memberId);
            address.markDefault();
        }
        addressRepository.save(address);
        log.info("Address added: {} for {}", address.getId(), memberId);
        return MemberAddressResponse.from(address);
    }

    public MemberAddressResponse update(UUID memberId, UUID addressId, MemberAddressRequest req) {
        MemberAddress address = find(memberId, addressId);
        address.update(req.alias().trim(), req.recipient(), req.phone(),
                req.zipcode(), req.address1(), req.address2());
        if (req.setDefault() && !address.isDefault()) {
            clearCurrentDefault(memberId);
            address.markDefault();
        }
        return MemberAddressResponse.from(address);
    }

    public MemberAddressResponse setDefault(UUID memberId, UUID addressId) {
        MemberAddress address = find(memberId, addressId);
        if (!address.isDefault()) {
            clearCurrentDefault(memberId);
            address.markDefault();
        }
        return MemberAddressResponse.from(address);
    }

    public void delete(UUID memberId, UUID addressId) {
        MemberAddress address = find(memberId, addressId);
        boolean wasDefault = address.isDefault();
        addressRepository.delete(address);

        if (wasDefault) {
            // ⚠ DELETE 를 먼저 DB 에 보낸다. 안 그러면 Hibernate 가 "새 기본 = 1" UPDATE 를 먼저 낼 수 있고,
            //    그 순간 기본 배송지가 두 행이 되어 유니크 인덱스가 ORA-00001 을 던진다.
            addressRepository.flush();
            // 기본을 지웠으면 남은 것 중 가장 먼저 등록한 주소가 승계한다.
            // 아무것도 안 하면 "주소는 있는데 기본이 없는" 상태가 되고, 그건 add() 의 첫 주소 규칙과 어긋난다.
            addressRepository.findFirstByMemberIdOrderByCreatedAtAsc(memberId)
                    .ifPresent(MemberAddress::markDefault);
        }
        log.info("Address deleted: {} for {}", addressId, memberId);
    }

    /**
     * 옛 계약({@code PATCH /api/members/me/shipping-address}) — "기본 배송지 하나 저장".
     *
     * <p>저장 위치만 {@code member.ship_*} 에서 주소록으로 옮겼다. 화면(주문서의 "이 주소를 기본
     * 배송지로 저장" 체크)이 계속 이 경로를 쓰므로 계약을 유지한다. 기본 배송지가 있으면 <b>덮어쓰고</b>,
     * 없으면 새로 만든다 — 옛 동작(컬럼 5개를 덮어쓰기)과 결과가 같다.
     *
     * <p>별칭은 이 경로로 받을 수 없어 "기본 배송지" 로 둔다. 사용자가 주소록 화면에서 고치면 된다.
     */
    public MemberAddressResponse saveDefault(UUID memberId, ShippingAddressRequest req) {
        MemberAddress current = addressRepository.findByMemberIdAndIsDefaultTrue(memberId).orElse(null);
        if (current != null) {
            current.update(current.getAlias(), req.recipient(), req.phone(),
                    req.zipcode(), req.address1(), req.address2());
            return MemberAddressResponse.from(current);
        }
        return add(memberId, new MemberAddressRequest("기본 배송지", req.recipient(), req.phone(),
                req.zipcode(), req.address1(), req.address2(), true));
    }

    /**
     * 현재 기본 배송지를 해제하고 <b>DB 까지 반영</b>한다.
     *
     * <p>{@code flush()} 가 핵심이다 — 없으면 해제 UPDATE 와 지정 UPDATE 의 순서를 Hibernate 가 정하고,
     * 뒤집히는 순간 기본 배송지가 두 행이 되어 유니크 인덱스에 걸린다. 눈에 안 띄는 줄이라
     * "불필요한 flush" 로 보고 지우기 쉬워서 여기 적어 둔다.
     */
    private void clearCurrentDefault(UUID memberId) {
        addressRepository.findByMemberIdAndIsDefaultTrue(memberId)
                .ifPresent(MemberAddress::unsetDefault);
        addressRepository.flush();
    }

    private MemberAddress find(UUID memberId, UUID addressId) {
        return addressRepository.findByIdAndMemberId(addressId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));
    }

    /**
     * 회원 삭제 정리(F-1) — 주소록 전체 삭제.
     *
     * <p>⚠ <b>F-1 에서 가장 뾰족한 자리다</b>: 수령인 이름·전화번호·주소가 들어 있어, 탈퇴가 뜻하는
     * "내 정보를 지워 달라"에 정면으로 걸린다. 같은 도메인이라 이벤트를 거치지 않고 직접 지운다.
     *
     * <p>기본 배송지 유니크 인덱스(V18, 함수기반)는 <b>삭제에는 걸리지 않는다</b> — 승계 로직도 필요 없다
     * (회원 자체가 사라지므로 승계할 주인이 없다).
     */
    public void deleteAllForMember(UUID memberId) {
        long deleted = addressRepository.deleteByMemberId(memberId);
        log.info("Addresses deleted for member {}: {}", memberId, deleted);
    }
}
