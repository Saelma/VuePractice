package com.glassvue.domain.member.service.query;

import com.glassvue.domain.member.dto.AdminMemberResponse;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.response.PageResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 회원 조회(B-11). 회원 목록·검색과 기본상세만 담당한다 — 그 회원의 주문·반품은 order,
 * 적립금·등급·이력은 point 의 admin 조회로 각각 붙는다(프론트가 조합). member 도메인은 order/point 를
 * 직접 참조하지 않는다(order 가 이미 member.Role 을 참조하므로 반대 방향을 만들면 순환이 된다).
 *
 * <p>탈퇴는 하드 삭제라(MemberService.withdraw) 이 목록엔 <b>현존 회원만</b> 보인다. 탈퇴 회원의 과거
 * 주문은 구매자 닉네임 스냅샷으로 order 쪽에 남는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberAdminQueryService {

    private final MemberRepository memberRepository;

    public PageResponse<AdminMemberResponse> search(String keyword, Pageable pageable) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        Page<Member> page = memberRepository.searchForAdmin(kw, withDefaultSort(pageable));
        return PageResponse.from(page.map(AdminMemberResponse::from));
    }

    public AdminMemberResponse get(UUID memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        return AdminMemberResponse.from(member);
    }

    /** 정렬을 안 주면 최신 가입 순으로 — 목록 기본값이 뒤죽박죽이면 관리자가 헷갈린다. */
    private Pageable withDefaultSort(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
