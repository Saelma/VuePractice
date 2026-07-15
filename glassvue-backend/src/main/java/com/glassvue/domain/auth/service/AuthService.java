package com.glassvue.domain.auth.service;

import com.glassvue.domain.auth.dto.LoginRequest;
import com.glassvue.domain.auth.dto.MemberResponse;
import com.glassvue.domain.auth.dto.SignupRequest;
import com.glassvue.domain.auth.dto.TokenResponse;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.security.JwtProperties;
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

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenStore refreshTokenStore;
    private final TokenBlacklist tokenBlacklist;

    public MemberResponse signup(SignupRequest req) {
        if (memberRepository.existsByLoginId(req.loginId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        Member member = Member.builder()
                .loginId(req.loginId())
                .password(passwordEncoder.encode(req.password()))
                .nickname(req.nickname())
                .role(Role.USER)
                .build();
        memberRepository.save(member);
        log.info("Member signed up: {}", member.getId());
        return MemberResponse.from(member);
    }

    public TokenResponse login(LoginRequest req) {
        Member member = memberRepository.findByLoginId(req.loginId())
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));
        if (!passwordEncoder.matches(req.password(), member.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        return issueTokens(member.getId(), member.getRole());
    }

    /** 리프레시 토큰으로 재발급(회전). 저장된 것과 일치해야만 허용. */
    public TokenResponse refresh(String refreshToken) {
        UUID memberId;
        try {
            memberId = UUID.fromString(jwtProvider.parse(refreshToken).getSubject());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        if (!refreshTokenStore.matches(memberId, refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        return issueTokens(member.getId(), member.getRole());
    }

    public void logout(UUID memberId, String accessToken) {
        refreshTokenStore.delete(memberId);
        try {
            Claims claims = jwtProvider.parse(accessToken);
            long ttl = claims.getExpiration().getTime() - System.currentTimeMillis();
            tokenBlacklist.blacklist(claims.getId(), ttl);
        } catch (Exception ignored) {
            // 이미 만료/무효인 access는 블랙리스트 불필요
        }
    }

    @Transactional(readOnly = true)
    public MemberResponse me(UUID memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        return MemberResponse.from(member);
    }

    private TokenResponse issueTokens(UUID memberId, Role role) {
        String access = jwtProvider.createAccessToken(memberId, role);
        String refresh = jwtProvider.createRefreshToken(memberId);
        refreshTokenStore.save(memberId, refresh);
        return TokenResponse.of(access, refresh, jwtProperties.accessTokenValidityMs() / 1000);
    }
}
