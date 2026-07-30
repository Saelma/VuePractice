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
import com.glassvue.global.mail.MailProperties;
import com.glassvue.global.mail.Mailer;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.security.JwtProperties;
import com.glassvue.global.security.JwtProvider;
import com.glassvue.global.security.LoginAttemptGuard;
import com.glassvue.global.security.PasswordPolicy;
import com.glassvue.global.security.PasswordResetTokenStore;
import com.glassvue.global.security.RefreshTokenStore;
import com.glassvue.global.security.TokenBlacklist;
import com.glassvue.global.security.TokenRevocationStore;
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
    private final TokenRevocationStore tokenRevocationStore;
    private final LoginAttemptGuard loginAttemptGuard;
    private final PasswordPolicy passwordPolicy;
    private final PasswordResetTokenStore passwordResetTokenStore;
    // 발송은 인프라라 global 어댑터를 그대로 주입한다(도메인 이벤트로 감쌀 만한 fan-out 이 아직 없다).
    private final Mailer mailer;
    private final MailProperties mailProperties;

    public MemberResponse signup(SignupRequest req) {
        if (memberRepository.existsByLoginId(req.loginId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        if (memberRepository.existsByNickname(req.nickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        // 이메일은 정규화한 값으로 검사하고 그 값을 그대로 저장한다 — 검사와 저장이 어긋나면
        // 유니크 제약(대소문자 구분)이 중복을 못 걸러낸다(Member.normalizeEmail 주석).
        String email = Member.normalizeEmail(req.email());
        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        // 비밀번호 정책(E-3) — 아이디·닉네임을 함께 봐야 하므로 DTO 애노테이션으로는 못 하는 검사다.
        passwordPolicy.validate(req.password(), req.loginId(), req.nickname());
        Member member = Member.builder()
                .loginId(req.loginId())
                .password(passwordEncoder.encode(req.password()))
                .nickname(req.nickname())
                .role(Role.USER)
                .email(email)
                .build();
        memberRepository.save(member);
        // 적립금 계정은 **가입 시** 만든다. 조회할 때 "없으면 만드는" 방식은 읽기가 쓰기를 하게 되고
        // 동시 조회에서 유니크 충돌도 난다(PointService.accountOf 주석 참조).
        pointService.openAccount(member.getId());
        log.info("Member signed up: {}", member.getId());
        // 갓 가입한 회원은 주소록이 비어 있다 — ship* 전부 null.
        return MemberResponse.of(member, null);
    }

    public TokenResponse login(LoginRequest req, String clientIp) {
        // ⚠ 차단 검사는 **DB 조회보다 먼저**다(E-1). 조회 뒤에 두면 없는 아이디는 카운트되기 전에
        // LOGIN_FAILED 로 빠져나가 카운터가 존재하는 계정에만 쌓이고, 그러면 차단 응답이
        // "이 계정은 있다" 를 알려주는 신호가 된다.
        if (loginAttemptGuard.isBlocked(req.loginId(), clientIp)) {
            throw new BusinessException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
        }
        Member member = memberRepository.findByLoginId(req.loginId())
                .orElseThrow(() -> {
                    loginAttemptGuard.recordFailure(req.loginId(), clientIp);
                    return new BusinessException(ErrorCode.LOGIN_FAILED);
                });
        if (!passwordEncoder.matches(req.password(), member.getPassword())) {
            loginAttemptGuard.recordFailure(req.loginId(), clientIp);
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        // 비번은 맞아도 정지된 계정은 로그인 불가(B-11 후속). 비번 검증 뒤에 둔 이유: 계정 열거를
        // 막으려면 존재/비번 여부를 먼저 통과한 뒤에야 정지 사실을 드러내야 한다.
        if (member.isSuspended()) {
            throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
        }
        // ⚠ 성공 처리는 정지 검사 **뒤**다. 정지 계정은 비밀번호가 맞아도 로그인이 아니므로,
        // 여기서 카운터를 리셋해 주면 정지된 계정으로 시도 예산을 무한히 새로 얻는 셈이 된다.
        loginAttemptGuard.recordSuccess(req.loginId());
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
        // 정지 회원은 갱신도 막는다 — 정지 시 refresh 토큰을 지우므로 대개 위 matches 에서 걸리지만,
        // 경합(정지 직전 발급된 토큰)까지 확실히 끊는다.
        if (member.isSuspended()) {
            throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
        }
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
                    sendResetMail(member, token);
                    return token;
                });
    }

    /**
     * 재설정 링크를 메일로 보낸다 (2026-07-29, B-10 마무리).
     *
     * <p><b>⚠ 여기서 예외를 던지면 열거 방지가 깨진다.</b> 이 메서드가 실패해 500 이 나가면
     * "그 아이디는 존재한다"가 드러난다 — 없는 아이디는 애초에 여기까지 안 오기 때문이다.
     * 그래서 {@code Mailer} 가 실패를 삼키고, 아래 두 갈래도 <b>조용히 넘어간다</b>:
     * <ul>
     *   <li><b>이메일이 없는 회원</b> — B-13(2026-07-29) 이전 가입자는 {@code email} 이 null 이다.
     *       보낼 곳이 없으니 발송만 건너뛴다. 토큰은 이미 발급됐고 응답도 그대로 200 이다.</li>
     *   <li><b>발송 채널이 없는 환경</b> — 운영 기본 프로파일이 그렇다({@code Mailer} 가 no-op).</li>
     * </ul>
     *
     * <p>⚠ <b>저장된 주소는 미검증이다</b>(B-13 — 확인 메일을 보내지 않았다). 오타가 있으면
     * 재설정 링크가 <b>남의 주소로 간다.</b> 소유 확인을 붙이기 전까지 남는 위험이라 여기 적어 둔다.
     */
    private void sendResetMail(Member member, String token) {
        if (member.getEmail() == null) {
            log.info("Password reset mail skipped — no email on member: {}", member.getId());
            return;
        }
        String link = mailProperties.baseUrl() + "/reset-password?token=" + token;
        mailer.send(member.getEmail(), "[Glassvue] 비밀번호 재설정 안내", """
                안녕하세요, %s님.

                아래 링크에서 새 비밀번호를 설정할 수 있습니다.

                %s

                이 링크는 30분 후 만료되며, 한 번 사용하면 더 이상 쓸 수 없습니다.
                본인이 요청한 것이 아니라면 이 메일을 무시하세요 — 비밀번호는 그대로 유지됩니다.
                """.formatted(member.getNickname(), link));
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
        // ⚠ 정책 검사는 **토큰 소비 뒤**다. 앞에 두면 약한 비밀번호를 넣어 본 사람이 토큰을 잃지 않고
        // 재시도할 수 있는데, 그건 편의 대신 링크의 단발성을 깨는 것이다 — 다시 요청하면 된다.
        passwordPolicy.validate(newPassword, member.getLoginId(), member.getNickname());
        member.updatePassword(passwordEncoder.encode(newPassword));
        refreshTokenStore.delete(memberId);
        // ⚠ 재설정은 계정을 되찾는 경로다 — 남이 들어와 있다면 그 access 토큰을 여기서 끊어야 한다
        // (refresh 삭제만으로는 만료까지 그대로 통한다). changePassword 와 같은 처리.
        tokenRevocationStore.revokeAll(memberId);
        log.info("Password reset completed: {}", memberId);
    }

    private TokenResponse issueTokens(Member member) {
        String access = jwtProvider.createAccessToken(member.getId(), member.getRole(), member.getNickname());
        String refresh = jwtProvider.createRefreshToken(member.getId());
        refreshTokenStore.save(member.getId(), refresh);
        return TokenResponse.of(access, refresh, jwtProperties.accessTokenValidityMs() / 1000);
    }
}
