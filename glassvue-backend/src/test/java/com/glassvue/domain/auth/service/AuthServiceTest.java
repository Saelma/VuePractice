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
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtProvider jwtProvider;
    @Mock JwtProperties jwtProperties;
    @Mock RefreshTokenStore refreshTokenStore;
    @Mock TokenBlacklist tokenBlacklist;
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
        assertErrorCode(() -> service.signup(new SignupRequest("kim", "pw", "닉")), ErrorCode.DUPLICATE_LOGIN_ID);
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("회원가입: 닉네임 중복 → DUPLICATE_NICKNAME, 저장 안 함")
    void signup_duplicateNickname() {
        when(memberRepository.existsByLoginId("kim")).thenReturn(false);
        when(memberRepository.existsByNickname("닉")).thenReturn(true);
        assertErrorCode(() -> service.signup(new SignupRequest("kim", "pw", "닉")), ErrorCode.DUPLICATE_NICKNAME);
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("회원가입: 새 아이디 → 저장 + 응답")
    void signup_ok() {
        when(memberRepository.existsByLoginId("kim")).thenReturn(false);
        when(memberRepository.existsByNickname("닉")).thenReturn(false);
        when(passwordEncoder.encode("pw")).thenReturn("ENC");
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));
        var res = service.signup(new SignupRequest("kim", "pw", "닉"));
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
}
