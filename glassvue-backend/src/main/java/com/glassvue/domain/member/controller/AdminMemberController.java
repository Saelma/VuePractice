package com.glassvue.domain.member.controller;

import com.glassvue.domain.member.dto.AdminMemberResponse;
import com.glassvue.domain.member.dto.RoleChangeRequest;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.response.PageResponse;
import com.glassvue.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin Member", description = "관리자 회원 API (ADMIN 전용, B-11)")
public interface AdminMemberController {

    @Operation(summary = "회원 목록·검색 (loginId·nickname·email 부분일치, 페이징)",
            description = "정렬 미지정 시 최신 가입 순. 탈퇴는 하드 삭제라 현존 회원만 나온다.")
    ResponseEntity<ApiResponse<PageResponse<AdminMemberResponse>>> list(
            @Parameter(description = "검색어(비우면 전체)") String keyword,
            @ParameterObject Pageable pageable);

    @Operation(summary = "회원 기본상세",
            description = "기본정보만. 그 회원의 적립금·등급은 /api/admin/points, 주문·반품은 /api/admin/orders 로 붙인다.")
    ResponseEntity<ApiResponse<AdminMemberResponse>> detail(@Parameter(description = "회원 id") UUID memberId);

    @Operation(summary = "회원 정지",
            description = "정지되면 로그인·토큰갱신·주문이 막힌다. 자기 계정은 정지할 수 없다(락아웃 방지).")
    ResponseEntity<ApiResponse<AdminMemberResponse>> suspend(
            @Parameter(hidden = true) AuthUser admin, @Parameter(description = "회원 id") UUID memberId);

    @Operation(summary = "회원 정지 해제")
    ResponseEntity<ApiResponse<AdminMemberResponse>> unsuspend(
            @Parameter(hidden = true) AuthUser admin, @Parameter(description = "회원 id") UUID memberId);

    @Operation(summary = "회원 강제 삭제 (SUPER_ADMIN 전용, B-24)",
            description = """
                    회원을 되돌릴 수 없이 지운다. 본인 탈퇴와 **같은 정리 경로**를 타므로 배송지·적립금·찜·쿠폰·
                    알림·재입고 구독·문의가 함께 지워진다(주문·리뷰·공지는 남는다 — 스냅샷으로 표시된다).
                    자기 계정과 SUPER_ADMIN 계정은 대상이 될 수 없다. 감사 이력에 MEMBER_DELETE 로 남는다.
                    """)
    ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(hidden = true) AuthUser admin, @Parameter(description = "회원 id") UUID memberId);

    @Operation(summary = "회원 역할 변경 (USER↔ADMIN)",
            description = "자기 계정은 변경할 수 없다(스스로 강등해 락아웃되는 것 방지).")
    ResponseEntity<ApiResponse<AdminMemberResponse>> changeRole(
            @Parameter(hidden = true) AuthUser admin,
            @Parameter(description = "회원 id") UUID memberId,
            @Valid RoleChangeRequest request);
}
