package com.glassvue.domain.auth.service;

import com.glassvue.domain.auth.dto.LoginRequest;
import com.glassvue.domain.auth.dto.MemberResponse;
import com.glassvue.domain.auth.dto.SignupRequest;
import com.glassvue.domain.auth.dto.TokenResponse;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.domain.member.service.query.MemberAddressQueryService;
import com.glassvue.domain.point.service.PointService;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.security.JwtProperties;
import com.glassvue.global.security.JwtProvider;
import com.glassvue.global.security.PasswordResetTokenStore;
import com.glassvue.global.security.RefreshTokenStore;
import com.glassvue.global.security.TokenBlacklist;
import io.jsonwebtoken.Claims;
import java.util.Optional;
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
    private final MemberAddressQueryService addressQueryService;
    private final PointService pointService;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenStore refreshTokenStore;
    private final TokenBlacklist tokenBlacklist;
    private final PasswordResetTokenStore passwordResetTokenStore;

    public MemberResponse signup(SignupRequest req) {
        if (memberRepository.existsByLoginId(req.loginId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        if (memberRepository.existsByNickname(req.nickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        Member member = Member.builder()
                .loginId(req.loginId())
                .password(passwordEncoder.encode(req.password()))
                .nickname(req.nickname())
                .role(Role.USER)
                .build();
        memberRepository.save(member);
        // 적립금 계정은 **가입 시** 만든다. 조회할 때 "없으면 만드는" 방식은 읽기가 쓰기를 하게 되고
        // 동시 조회에서 유니크 충돌도 난다(PointService.accountOf 주석 참조).
        pointService.openAccount(member.getId());
        log.info("Member signed up: {}", member.getId());
        // 갓 가입한 회원은 주소록이 비어 있다 — ship* 전부 null.
        return MemberResponse.of(member, null);
    }

    public TokenResponse login(LoginRequest req) {
        Member member = memberRepository.findByLoginId(req.loginId())
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));
        if (!passwordEncoder.matches(req.password(), member.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        return issueTokens(member);
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
        return issueTokens(member);
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
        // ship* 는 주소록의 기본 항목에서 온다(V18 이전엔 member 컬럼이었다).
        // 프론트가 로그인 직후 이 응답으로 주문서 자동 채움 값을 갖는다.
        return MemberResponse.of(member, addressQueryService.findDefault(memberId));
    }

    /**
     * 재설정 토큰 발급. 열거 공격 방지를 위해 <b>아이디가 없어도 성공처럼</b> 반환한다
     * (없으면 토큰만 안 만들 뿐, 호출자는 구분할 수 없다). 반환 토큰은 dev에서만 화면에
     * 노출되고, 운영은 원래 발송 채널로만 나가야 한다(현재는 채널이 없어 null).
     *
     * @return 발급된 토큰(대상 회원이 있을 때만), 없으면 empty
     */
    public Optional<String> requestPasswordReset(String loginId) {
        return memberRepository.findByLoginId(loginId)
                .map(member -> {
                    String token = passwordResetTokenStore.issue(member.getId());
                    log.info("Password reset requested: {}", member.getId());
                    return token;
                });
    }

    /**
     * 재설정 토큰 검증 후 비밀번호 변경. 토큰은 단발성(consume 시 삭제)이고, 변경과 동시에
     * 저장된 refresh를 지워 다른 세션을 무효화한다(비밀번호 바꾸면 재로그인 — changePassword와 동일).
     */
    public void confirmPasswordReset(String token, String newPassword) {
        UUID memberId = passwordResetTokenStore.consume(token);
        if (memberId == null) {
            throw new BusinessException(ErrorCode.INVALID_RESET_TOKEN);
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_RESET_TOKEN));
        member.updatePassword(passwordEncoder.encode(newPassword));
        refreshTokenStore.delete(memberId);
        log.info("Password reset completed: {}", memberId);
    }

    private TokenResponse issueTokens(Member member) {
        String access = jwtProvider.createAccessToken(member.getId(), member.getRole(), member.getNickname());
        String refresh = jwtProvider.createRefreshToken(member.getId());
        refreshTokenStore.save(member.getId(), refresh);
        return TokenResponse.of(access, refresh, jwtProperties.accessTokenValidityMs() / 1000);
    }
}
