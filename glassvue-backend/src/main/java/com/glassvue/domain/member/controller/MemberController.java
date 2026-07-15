package com.glassvue.domain.member.controller;

import com.glassvue.domain.auth.dto.MemberResponse;
import com.glassvue.domain.member.dto.NicknameUpdateRequest;
import com.glassvue.domain.member.dto.PasswordUpdateRequest;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

@Tag(name = "Member", description = "내 계정 관리 API")
public interface MemberController {

    @Operation(summary = "닉네임 변경")
    ResponseEntity<ApiResponse<MemberResponse>> changeNickname(
            @Parameter(hidden = true) AuthUser user,
            @Valid NicknameUpdateRequest request);

    @Operation(summary = "비밀번호 변경 (현재 비밀번호 확인)")
    ResponseEntity<ApiResponse<Void>> changePassword(
            @Parameter(hidden = true) AuthUser user,
            @Valid PasswordUpdateRequest request);

    @Operation(summary = "회원 탈퇴")
    ResponseEntity<ApiResponse<Void>> withdraw(
            @Parameter(hidden = true) AuthUser user,
            @Parameter(hidden = true) String authorization);
}
