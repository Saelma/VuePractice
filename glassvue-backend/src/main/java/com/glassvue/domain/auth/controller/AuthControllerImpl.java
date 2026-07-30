package com.glassvue.domain.auth.controller;

import com.glassvue.domain.auth.dto.LoginRequest;
import com.glassvue.domain.auth.dto.MemberResponse;
import com.glassvue.domain.auth.dto.PasswordResetConfirmRequest;
import com.glassvue.domain.auth.dto.PasswordResetRequest;
import com.glassvue.domain.auth.dto.PasswordResetResponse;
import com.glassvue.domain.auth.dto.RefreshRequest;
import com.glassvue.domain.auth.dto.SignupRequest;
import com.glassvue.domain.auth.dto.TokenResponse;
import com.glassvue.domain.auth.service.AuthService;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.security.ClientIpResolver;
import com.glassvue.global.security.AuthUser;
import com.glassvue.global.security.LoginUser;
import com.glassvue.global.security.PasswordResetProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthControllerImpl implements AuthController {

    private final AuthService authService;
    private final PasswordResetProperties passwordResetProperties;

    @Override
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<MemberResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(authService.signup(request)));
    }

    @Override
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        // 클라이언트 IP 는 nginx 뒤라 그냥 getRemoteAddr() 을 쓰면 전부 127.0.0.1 이 된다
        // (ClientIpResolver 주석 — 그 상태로 IP 제한을 걸면 한 사람 때문에 전원이 잠긴다).
        return ResponseEntity.ok(ApiResponse.ok(
                authService.login(request, ClientIpResolver.resolve(httpRequest))));
    }

    @Override
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(request.refreshToken())));
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @LoginUser AuthUser user,
            @RequestHeader("Authorization") String authorization) {
        authService.logout(user.id(), authorization.substring(7));
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Override
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> me(@LoginUser AuthUser user) {
        return ResponseEntity.ok(ApiResponse.ok(authService.me(user.id())));
    }

    @Override
    @PostMapping("/password-reset/request")
    public ResponseEntity<ApiResponse<PasswordResetResponse>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request) {
        // 열거 공격 방지 — 아이디 존재 여부와 무관하게 항상 200.
        // 토큰은 발송 채널이 없는 dev에서만 응답에 실어 화면에서 링크를 확인한다(운영은 항상 null).
        String token = authService.requestPasswordReset(request.loginId()).orElse(null);
        String exposed = passwordResetProperties.exposeToken() ? token : null;
        return ResponseEntity.ok(ApiResponse.ok(PasswordResetResponse.of(exposed)));
    }

    @Override
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.confirmPasswordReset(request.token(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
