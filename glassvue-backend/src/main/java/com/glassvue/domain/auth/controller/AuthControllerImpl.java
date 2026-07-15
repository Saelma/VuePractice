package com.glassvue.domain.auth.controller;

import com.glassvue.domain.auth.dto.LoginRequest;
import com.glassvue.domain.auth.dto.MemberResponse;
import com.glassvue.domain.auth.dto.RefreshRequest;
import com.glassvue.domain.auth.dto.SignupRequest;
import com.glassvue.domain.auth.dto.TokenResponse;
import com.glassvue.domain.auth.service.AuthService;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.security.AuthUser;
import com.glassvue.global.security.LoginUser;
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

    @Override
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<MemberResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(authService.signup(request)));
    }

    @Override
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
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
}
