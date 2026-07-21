package com.glassvue.domain.member.controller;

import com.glassvue.domain.auth.dto.MemberResponse;
import com.glassvue.domain.member.dto.NicknameUpdateRequest;
import com.glassvue.domain.member.dto.PasswordUpdateRequest;
import com.glassvue.domain.member.dto.ShippingAddressRequest;
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

    @Operation(summary = "기본 배송지 저장",
            description = "주문서에 자동으로 채워 넣기 위한 값. 주문에는 이 값을 복사(스냅샷)하므로 나중에 바꿔도 과거 주문의 배송지는 변하지 않는다.")
    ResponseEntity<ApiResponse<MemberResponse>> updateShippingAddress(
            @Parameter(hidden = true) AuthUser user,
            @Valid ShippingAddressRequest request);

    @Operation(summary = "비밀번호 변경 (현재 비밀번호 확인)")
    ResponseEntity<ApiResponse<Void>> changePassword(
            @Parameter(hidden = true) AuthUser user,
            @Valid PasswordUpdateRequest request);

    @Operation(summary = "회원 탈퇴")
    ResponseEntity<ApiResponse<Void>> withdraw(
            @Parameter(hidden = true) AuthUser user,
            @Parameter(hidden = true) String authorization);
}
