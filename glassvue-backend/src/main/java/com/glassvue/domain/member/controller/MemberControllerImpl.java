package com.glassvue.domain.member.controller;

import com.glassvue.domain.auth.dto.MemberResponse;
import com.glassvue.domain.member.dto.EmailUpdateRequest;
import com.glassvue.domain.member.dto.EmailVerificationRequest;
import com.glassvue.domain.member.dto.NicknameUpdateRequest;
import com.glassvue.domain.member.dto.PasswordUpdateRequest;
import com.glassvue.domain.member.dto.ShippingAddressRequest;
import com.glassvue.domain.member.service.MemberService;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.security.AuthUser;
import com.glassvue.global.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members")
public class MemberControllerImpl implements MemberController {

    private final MemberService memberService;

    @Override
    @PatchMapping("/me/nickname")
    public ResponseEntity<ApiResponse<MemberResponse>> changeNickname(
            @LoginUser AuthUser user,
            @Valid @RequestBody NicknameUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(memberService.changeNickname(user.id(), request.nickname())));
    }

    @Override
    @PatchMapping("/me/email")
    public ResponseEntity<ApiResponse<MemberResponse>> changeEmail(
            @LoginUser AuthUser user,
            @Valid @RequestBody EmailUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(memberService.changeEmail(user.id(), request.email())));
    }

    // 인증 흐름은 둘 다 **본인만**(/api/members/** 는 SecurityConfig 가 한 줄로 authenticated).
    @Override
    @PostMapping("/me/email/verification")
    public ResponseEntity<ApiResponse<Void>> sendEmailVerification(@LoginUser AuthUser user) {
        memberService.sendEmailVerification(user.id());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Override
    @PostMapping("/me/email/verification/confirm")
    public ResponseEntity<ApiResponse<MemberResponse>> confirmEmailVerification(
            @LoginUser AuthUser user,
            @Valid @RequestBody EmailVerificationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                memberService.confirmEmailVerification(user.id(), request.code())));
    }

    @Override
    @PatchMapping("/me/shipping-address")
    public ResponseEntity<ApiResponse<MemberResponse>> updateShippingAddress(
            @LoginUser AuthUser user,
            @Valid @RequestBody ShippingAddressRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(memberService.updateShippingAddress(user.id(), request)));
    }

    @Override
    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @LoginUser AuthUser user,
            @Valid @RequestBody PasswordUpdateRequest request) {
        memberService.changePassword(user.id(), request.currentPassword(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Override
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @LoginUser AuthUser user,
            @RequestHeader("Authorization") String authorization) {
        memberService.withdraw(user.id(), authorization.substring(7));
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
