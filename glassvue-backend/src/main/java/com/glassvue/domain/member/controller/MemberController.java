package com.glassvue.domain.member.controller;

import com.glassvue.domain.auth.dto.MemberResponse;
import com.glassvue.domain.member.dto.EmailUpdateRequest;
import com.glassvue.domain.member.dto.EmailVerificationRequest;
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

    @Operation(summary = "이메일 등록·변경",
            description = "비밀번호 재설정 링크를 받을 주소. 기존 회원은 값이 없어 이 API 가 유일한 수집 경로다. "
                    + "⚠ 저장만 하고 확인 메일을 자동 발송하지는 않는다 — 인증은 별도 API(B-14)로 사용자가 시작한다. "
                    + "주소를 바꾸면 인증 상태가 함께 풀린다.")
    ResponseEntity<ApiResponse<MemberResponse>> changeEmail(
            @Parameter(hidden = true) AuthUser user,
            @Valid EmailUpdateRequest request);

    @Operation(summary = "이메일 인증번호 발송 (B-14)",
            description = "등록된 주소로 6자리 인증번호를 보낸다. 이미 인증됐거나 이메일이 없으면 거부한다. "
                    + "발송 채널이 없는 환경(운영 기본)에서는 조용히 아무것도 나가지 않는다.")
    ResponseEntity<ApiResponse<Void>> sendEmailVerification(
            @Parameter(hidden = true) AuthUser user);

    @Operation(summary = "이메일 인증번호 확인 (B-14)",
            description = "맞으면 이메일이 인증됨으로 바뀐다. ⚠ 만료·횟수초과·불일치를 구분하지 않는다 — "
                    + "구분해 주면 남은 시도 횟수를 세어 볼 수 있다. 5회 틀리면 코드가 폐기되어 재발송해야 한다.")
    ResponseEntity<ApiResponse<MemberResponse>> confirmEmailVerification(
            @Parameter(hidden = true) AuthUser user,
            @Valid EmailVerificationRequest request);

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
