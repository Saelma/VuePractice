package com.glassvue.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassvue.domain.auth.dto.LoginRequest;
import com.glassvue.domain.auth.dto.SignupRequest;
import com.glassvue.domain.auth.dto.TokenResponse;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.security.JwtProperties;
import com.glassvue.global.security.JwtProvider;
import com.glassvue.global.security.PasswordResetTokenStore;
import com.glassvue.global.security.PasswordPolicy;
import com.glassvue.global.security.PasswordResetRequestGuard;
import com.glassvue.global.security.RefreshTokenStore;
import com.glassvue.global.security.TokenBlacklist;
import com.glassvue.global.security.LoginAttemptGuard;
import com.glassvue.global.security.TokenRevocationStore;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock MemberRepository memberRepository;
    // V18 이후 me() 의 ship* 는 주소록에서 온다(가입 직후는 항상 비어 있다).
    @Mock com.glassvue.domain.member.service.query.MemberAddressQueryService addressQueryService;
    @Mock com.glassvue.domain.point.service.PointService pointService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtProvider jwtProvider;
    @Mock JwtProperties jwtProperties;
    @Mock RefreshTokenStore refreshTokenStore;
    @Mock TokenBlacklist tokenBlacklist;
    @Mock TokenRevocationStore tokenRevocationStore;
    @Mock LoginAttemptGuard loginAttemptGuard;
    @Mock PasswordPolicy passwordPolicy; // 규칙은 PasswordPolicyTest·API 통합테스트가 본다
    @Mock PasswordResetRequestGuard resetRequestGuard;
    @Mock PasswordResetTokenStore passwordResetTokenStore;
    @Mock com.glassvue.global.mail.Mailer mailer;
    // MailProperties 는 record 라 목이 아니라 실값을 쓴다 — 링크 조립 결과를 그대로 검증하려고.
    @Spy com.glassvue.global.mail.MailProperties mailProperties =
            new com.glassvue.global.mail.MailProperties("no-reply@test.local", "https://app.test");
    @InjectMocks AuthService service;

    private static final String IP = "10.0.2.99";

    private Member member() {
        return Member.builder().loginId("kim").password("HASH").nickname("김철수").role(Role.USER).build();
    }
    private static void assertErrorCode(Runnable r, ErrorCode expected) {
        assertThatThrownBy(r::run).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(expected);
    }

    @Test
    @DisplayName("회원가입: 아이디 중복 → DUPLICATE_LOGIN_ID, 저장 안 함")
    void signup_duplicate() {
        when(memberRepository.existsByLoginId("kim")).thenReturn(true);
        assertErrorCode(() -> service.signup(new SignupRequest("kim", "pw", "닉", "kim@example.com")), ErrorCode.DUPLICATE_LOGIN_ID);
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("회원가입: 닉네임 중복 → DUPLICATE_NICKNAME, 저장 안 함")
    void signup_duplicateNickname() {
        when(memberRepository.existsByLoginId("kim")).thenReturn(false);
        when(memberRepository.existsByNickname("닉")).thenReturn(true);
        assertErrorCode(() -> service.signup(new SignupRequest("kim", "pw", "닉", "kim@example.com")), ErrorCode.DUPLICATE_NICKNAME);
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("회원가입: 새 아이디 → 저장 + 응답")
    void signup_ok() {
        when(memberRepository.existsByLoginId("kim")).thenReturn(false);
        when(memberRepository.existsByNickname("닉")).thenReturn(false);
        when(passwordEncoder.encode("pw")).thenReturn("ENC");
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));
        var res = service.signup(new SignupRequest("kim", "pw", "닉", "kim@example.com"));
        assertThat(res.nickname()).isEqualTo("닉");
        verify(memberRepository).save(any(Member.class));
    }

    @Test
    @DisplayName("로그인: 없는 아이디 → LOGIN_FAILED")
    void login_notFound() {
        when(memberRepository.findByLoginId("nope")).thenReturn(Optional.empty());
        assertErrorCode(() -> service.login(new LoginRequest("nope", "pw"), IP), ErrorCode.LOGIN_FAILED);
    }

    @Test
    @DisplayName("로그인: 비번 불일치 → LOGIN_FAILED")
    void login_wrongPassword() {
        when(memberRepository.findByLoginId("kim")).thenReturn(Optional.of(member()));
        when(passwordEncoder.matches("wrong", "HASH")).thenReturn(false);
        assertErrorCode(() -> service.login(new LoginRequest("kim", "wrong"), IP), ErrorCode.LOGIN_FAILED);
    }

    @Test
    @DisplayName("로그인: 성공 → 토큰 발급 + 리프레시 저장")
    void login_ok() {
        Member m = member();
        when(memberRepository.findByLoginId("kim")).thenReturn(Optional.of(m));
        when(passwordEncoder.matches("pw", "HASH")).thenReturn(true);
        when(jwtProvider.createAccessToken(any(), any(), any())).thenReturn("AT");
        when(jwtProvider.createRefreshToken(any())).thenReturn("RT");
        when(jwtProperties.accessTokenValidityMs()).thenReturn(1_800_000L);
        TokenResponse res = service.login(new LoginRequest("kim", "pw"), IP);
        assertThat(res.accessToken()).isEqualTo("AT");
        verify(refreshTokenStore).save(m.getId(), "RT");
    }

    @Test
    @DisplayName("재발급: 저장된 리프레시와 불일치 → INVALID_TOKEN")
    void refresh_mismatch() {
        UUID mid = UUID.randomUUID();
        io.jsonwebtoken.Claims claims = org.mockito.Mockito.mock(io.jsonwebtoken.Claims.class);
        when(claims.getSubject()).thenReturn(mid.toString());
        when(jwtProvider.parse("RT")).thenReturn(claims);
        when(refreshTokenStore.matches(mid, "RT")).thenReturn(false);
        assertErrorCode(() -> service.refresh("RT"), ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("로그인: 정지된 계정 → ACCOUNT_SUSPENDED (비번은 맞아도)")
    void login_suspended() {
        Member m = member();
        m.suspend();
        when(memberRepository.findByLoginId("kim")).thenReturn(Optional.of(m));
        when(passwordEncoder.matches("pw", "HASH")).thenReturn(true);
        assertErrorCode(() -> service.login(new LoginRequest("kim", "pw"), IP), ErrorCode.ACCOUNT_SUSPENDED);
    }

    @Test
    @DisplayName("재발급: 정지된 계정 → ACCOUNT_SUSPENDED")
    void refresh_suspended() {
        UUID mid = UUID.randomUUID();
        Member m = member();
        m.suspend();
        io.jsonwebtoken.Claims claims = org.mockito.Mockito.mock(io.jsonwebtoken.Claims.class);
        when(claims.getSubject()).thenReturn(mid.toString());
        when(jwtProvider.parse("RT")).thenReturn(claims);
        when(refreshTokenStore.matches(mid, "RT")).thenReturn(true);
        when(memberRepository.findById(mid)).thenReturn(Optional.of(m));
        assertErrorCode(() -> service.refresh("RT"), ErrorCode.ACCOUNT_SUSPENDED);
    }

    @Test
    @DisplayName("재설정 요청: 있는 아이디 → 토큰 발급 후 반환")
    void requestReset_existing() {
        Member m = member();
        when(memberRepository.findByLoginId("kim")).thenReturn(Optional.of(m));
        when(passwordResetTokenStore.issue(m.getId())).thenReturn("RESET-TOKEN");
        assertThat(service.requestPasswordReset("kim", IP)).contains("RESET-TOKEN");
        verify(passwordResetTokenStore).issue(m.getId());
    }

    @Test
    @DisplayName("재설정 요청: 이메일이 있으면 재설정 링크가 담긴 메일이 그 주소로 나간다 (B-10 마무리)")
    void requestReset_sendsMail() {
        Member m = com.glassvue.domain.member.entity.Member.builder()
                .loginId("kim").password("ENC").nickname("김철수")
                .role(com.glassvue.domain.member.entity.Role.USER).email("kim@test.local").build();
        when(memberRepository.findByLoginId("kim")).thenReturn(Optional.of(m));
        when(passwordResetTokenStore.issue(m.getId())).thenReturn("RESET-TOKEN");

        service.requestPasswordReset("kim", IP);

        org.mockito.ArgumentCaptor<String> to = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<String> body = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mailer).send(to.capture(), any(), body.capture());
        assertThat(to.getValue()).isEqualTo("kim@test.local");
        // 링크는 설정의 base-url 로 만든다(요청 Host 헤더가 아니다 — host header injection 방지)
        assertThat(body.getValue()).contains("https://app.test/reset-password?token=RESET-TOKEN");
    }

    @Test
    @DisplayName("⚠ 재설정 요청: 이메일이 없는 기존 회원이면 발송만 건너뛴다 — 토큰은 그대로 발급된다")
    void requestReset_noEmail_skipsMailButStillIssues() {
        Member m = member(); // email 없음 (B-13 이전 가입자 재현)
        when(memberRepository.findByLoginId("kim")).thenReturn(Optional.of(m));
        when(passwordResetTokenStore.issue(m.getId())).thenReturn("RESET-TOKEN");

        assertThat(service.requestPasswordReset("kim", IP)).contains("RESET-TOKEN");

        verify(mailer, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("재설정 요청: 없는 아이디 → empty, 토큰 발급 안 함(열거 방지) + 메일도 안 나간다")
    void requestReset_absent() {
        when(memberRepository.findByLoginId("nope")).thenReturn(Optional.empty());
        assertThat(service.requestPasswordReset("nope", IP)).isEmpty();
        verify(passwordResetTokenStore, never()).issue(any());
        verify(mailer, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("재설정 확정: 유효 토큰 → 비번 변경 + 리프레시 삭제")
    void confirmReset_ok() {
        Member m = member();
        when(passwordResetTokenStore.consume("RESET-TOKEN")).thenReturn(m.getId());
        when(memberRepository.findById(m.getId())).thenReturn(Optional.of(m));
        when(passwordEncoder.encode("newpw12345")).thenReturn("NEWENC");
        service.confirmPasswordReset("RESET-TOKEN", "newpw12345");
        assertThat(m.getPassword()).isEqualTo("NEWENC");
        verify(refreshTokenStore).delete(m.getId());
    }

    @Test
    @DisplayName("재설정 확정: 무효/만료 토큰 → INVALID_RESET_TOKEN, 변경 안 함")
    void confirmReset_invalidToken() {
        when(passwordResetTokenStore.consume("BAD")).thenReturn(null);
        assertErrorCode(() -> service.confirmPasswordReset("BAD", "newpw12345"), ErrorCode.INVALID_RESET_TOKEN);
        verify(refreshTokenStore, never()).delete(any());
    }
}
