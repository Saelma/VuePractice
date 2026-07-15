package com.glassvue.domain.member.service;

import com.glassvue.domain.auth.dto.MemberResponse;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.repository.MemberRepository;
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
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenStore refreshTokenStore;
    private final TokenBlacklist tokenBlacklist;
    private final JwtProvider jwtProvider;

    public MemberResponse changeNickname(UUID memberId, String nickname) {
        Member member = find(memberId);
        member.updateNickname(nickname);
        // 주의: 토큰의 nickname claim은 다음 로그인/refresh 때 갱신됨(과거 글의 작성자명은 그대로).
        return MemberResponse.from(member);
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

    private Member find(UUID memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
