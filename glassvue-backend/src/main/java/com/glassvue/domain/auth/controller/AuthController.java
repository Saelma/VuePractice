package com.glassvue.domain.auth.controller;

import com.glassvue.domain.auth.dto.LoginRequest;
import com.glassvue.domain.auth.dto.MemberResponse;
import com.glassvue.domain.auth.dto.PasswordResetConfirmRequest;
import com.glassvue.domain.auth.dto.PasswordResetRequest;
import com.glassvue.domain.auth.dto.PasswordResetResponse;
import com.glassvue.domain.auth.dto.RefreshRequest;
import com.glassvue.domain.auth.dto.SignupRequest;
import com.glassvue.domain.auth.dto.TokenResponse;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

@Tag(name = "Auth", description = "인증 API (JWT)")
public interface AuthController {

    @Operation(summary = "회원가입")
    ResponseEntity<ApiResponse<MemberResponse>> signup(@Valid SignupRequest request);

    @Operation(summary = "로그인 (access + refresh 발급)")
    ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid LoginRequest request,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) jakarta.servlet.http.HttpServletRequest httpRequest);

    @Operation(summary = "토큰 재발급 (refresh 회전)")
    ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid RefreshRequest request);

    @Operation(summary = "로그아웃 (refresh 삭제 + access 블랙리스트)")
    ResponseEntity<ApiResponse<Void>> logout(
            @Parameter(hidden = true) AuthUser user,
            @Parameter(hidden = true) String authorization);

    @Operation(summary = "내 정보")
    ResponseEntity<ApiResponse<MemberResponse>> me(@Parameter(hidden = true) AuthUser user);

    @Operation(summary = "비밀번호 재설정 요청 (아이디로 재설정 링크 발급)")
    ResponseEntity<ApiResponse<PasswordResetResponse>> requestPasswordReset(
            @Valid PasswordResetRequest request,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) jakarta.servlet.http.HttpServletRequest httpRequest);

    @Operation(summary = "비밀번호 재설정 확정 (토큰 + 새 비밀번호)")
    ResponseEntity<ApiResponse<Void>> confirmPasswordReset(@Valid PasswordResetConfirmRequest request);
}
