package com.glassvue.domain.member.service;

import com.glassvue.domain.auth.dto.MemberResponse;
import com.glassvue.domain.member.dto.ShippingAddressRequest;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.domain.member.service.command.MemberAddressCommandService;
import com.glassvue.domain.member.service.query.MemberAddressQueryService;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.security.JwtProvider;
import com.glassvue.global.security.RefreshTokenStore;
import com.glassvue.global.security.TokenBlacklist;
import io.jsonwebtoken.Claims;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 계정 관리 — 닉네임/비밀번호 변경, 회원 탈퇴. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MemberService {

    private final MemberRepository memberRepository;
    private final MemberAddressCommandService addressCommandService;
    private final MemberAddressQueryService addressQueryService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenStore refreshTokenStore;
    private final TokenBlacklist tokenBlacklist;
    private final JwtProvider jwtProvider;

    /** 관리자 회원 id 목록 — 관리자 대상 알림(재고 부족 등)을 만들 때 쓰는 다른 도메인용 공개 API. */
    @Transactional(readOnly = true)
    public java.util.List<UUID> adminIds() {
        return memberRepository.findIdsByRole(Role.ADMIN);
    }

    /** 정지 여부 — 주문(order) 도메인이 정지 회원의 주문을 막을 때 쓰는 공개 API(B-11 후속). */
    @Transactional(readOnly = true)
    public boolean isSuspended(UUID memberId) {
        return find(memberId).isSuspended();
    }

    public MemberResponse changeNickname(UUID memberId, String nickname) {
        Member member = find(memberId);
        // 닉네임은 유니크. 본인은 제외해 같은 값 재저장은 허용하고, 남이 쓰는 값이면 막는다.
        if (memberRepository.existsByNicknameAndIdNot(nickname, memberId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        member.updateNickname(nickname);
        // 주의: 토큰의 nickname claim은 다음 로그인/refresh 때 갱신됨(과거 글의 작성자명은 그대로).
        return withDefaultAddress(member);
    }

    /**
     * 기본 배송지 저장 — 주문서에 자동으로 채워 넣기 위한 값. 주문에는 복사(스냅샷)된다.
     *
     * <p>2026-07-24(V18)부터 <b>저장 위치가 주소록</b>이다. {@code member.ship_*} 컬럼에 쓰던 것을
     * 기본 배송지 항목 upsert 로 바꿨다 — API 계약(경로·요청·응답)은 그대로라 화면은 손대지 않았다.
     */
    public MemberResponse updateShippingAddress(UUID memberId, ShippingAddressRequest req) {
        Member member = find(memberId);
        addressCommandService.saveDefault(memberId, req);
        return withDefaultAddress(member);
    }

    public void changePassword(UUID memberId, String currentPassword, String newPassword) {
        Member member = find(memberId);
        if (!passwordEncoder.matches(currentPassword, member.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
        member.updatePassword(passwordEncoder.encode(newPassword));
        refreshTokenStore.delete(memberId); // 다른 기기 세션 무효화(재로그인 유도)
        log.info("Password changed: {}", memberId);
    }

    public void withdraw(UUID memberId, String accessToken) {
        Member member = find(memberId);
        refreshTokenStore.delete(memberId);
        blacklistAccess(accessToken);
        memberRepository.delete(member);
        log.info("Member withdrawn: {}", memberId);
    }

    private void blacklistAccess(String accessToken) {
        try {
            Claims claims = jwtProvider.parse(accessToken);
            long ttl = claims.getExpiration().getTime() - System.currentTimeMillis();
            tokenBlacklist.blacklist(claims.getId(), ttl);
        } catch (Exception ignored) {
            // 이미 만료/무효면 불필요
        }
    }

    /** MemberResponse의 ship* 필드는 주소록의 기본 항목에서 온다(V18 이전엔 member 컬럼이었다). */
    private MemberResponse withDefaultAddress(Member member) {
        return MemberResponse.of(member, addressQueryService.findDefault(member.getId()));
    }

    private Member find(UUID memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
