package com.glassvue.domain.member.controller;

import com.glassvue.domain.member.dto.MemberAddressRequest;
import com.glassvue.domain.member.dto.MemberAddressResponse;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

/**
 * 배송지 주소록 API (2026-07-24, 백로그 B-5).
 *
 * <p>전부 <b>본인 것만</b> 다룬다 — 경로에 memberId 가 없고 토큰에서 읽는다. 남의 주소를 조작할
 * 경로 자체가 없다(주문 목록의 소유 스코프를 서버가 고정한 것과 같은 방식).
 * 인증은 SecurityConfig 의 {@code /api/members/**} 한 줄이 이미 덮는다.
 */
@Tag(name = "MemberAddress", description = "배송지 주소록 API")
public interface MemberAddressController {

    @Operation(summary = "내 주소록 목록", description = "기본 배송지가 맨 위, 그다음 등록 순.")
    ResponseEntity<ApiResponse<List<MemberAddressResponse>>> myAddresses(
            @Parameter(hidden = true) AuthUser user);

    @Operation(summary = "배송지 추가",
            description = "첫 주소는 setDefault 와 무관하게 기본 배송지가 된다. 최대 10개.")
    ResponseEntity<ApiResponse<MemberAddressResponse>> add(
            @Parameter(hidden = true) AuthUser user,
            @Valid MemberAddressRequest request);

    @Operation(summary = "배송지 수정",
            description = "주문에는 배송지가 스냅샷으로 복사되므로 과거 주문의 배송지는 바뀌지 않는다.")
    ResponseEntity<ApiResponse<MemberAddressResponse>> update(
            @Parameter(hidden = true) AuthUser user,
            UUID addressId,
            @Valid MemberAddressRequest request);

    @Operation(summary = "기본 배송지 지정",
            description = "기존 기본 배송지는 자동으로 해제된다. 회원당 기본 배송지는 하나뿐이다(DB 유니크).")
    ResponseEntity<ApiResponse<MemberAddressResponse>> setDefault(
            @Parameter(hidden = true) AuthUser user,
            UUID addressId);

    @Operation(summary = "배송지 삭제",
            description = "기본 배송지를 지우면 남은 것 중 가장 먼저 등록한 주소가 기본이 된다.")
    ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(hidden = true) AuthUser user,
            UUID addressId);
}
