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
import com.glassvue.global.security.RefreshTokenStore;
import com.glassvue.global.security.TokenBlacklist;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
    @Mock PasswordResetTokenStore passwordResetTokenStore;
    @InjectMocks AuthService service;

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
        assertErrorCode(() -> service.login(new LoginRequest("nope", "pw")), ErrorCode.LOGIN_FAILED);
    }

    @Test
    @DisplayName("로그인: 비번 불일치 → LOGIN_FAILED")
    void login_wrongPassword() {
        when(memberRepository.findByLoginId("kim")).thenReturn(Optional.of(member()));
        when(passwordEncoder.matches("wrong", "HASH")).thenReturn(false);
        assertErrorCode(() -> service.login(new LoginRequest("kim", "wrong")), ErrorCode.LOGIN_FAILED);
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
        TokenResponse res = service.login(new LoginRequest("kim", "pw"));
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
        assertErrorCode(() -> service.login(new LoginRequest("kim", "pw")), ErrorCode.ACCOUNT_SUSPENDED);
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
        assertThat(service.requestPasswordReset("kim")).contains("RESET-TOKEN");
        verify(passwordResetTokenStore).issue(m.getId());
    }

    @Test
    @DisplayName("재설정 요청: 없는 아이디 → empty, 토큰 발급 안 함(열거 방지)")
    void requestReset_absent() {
        when(memberRepository.findByLoginId("nope")).thenReturn(Optional.empty());
        assertThat(service.requestPasswordReset("nope")).isEmpty();
        verify(passwordResetTokenStore, never()).issue(any());
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
