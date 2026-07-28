package com.glassvue.domain.member.controller;

import com.glassvue.domain.member.dto.AdminMemberResponse;
import com.glassvue.domain.member.dto.RoleChangeRequest;
import com.glassvue.domain.member.service.command.MemberAdminCommandService;
import com.glassvue.domain.member.service.query.MemberAdminQueryService;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.response.PageResponse;
import com.glassvue.global.security.AuthUser;
import com.glassvue.global.security.LoginUser;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 회원 API. {@code /api/admin/**} 한 줄(SecurityConfig)로 ADMIN 보호된다.
 * 회원 자체 정보만 다루고, 크로스도메인(주문·적립금)은 각 도메인 admin 엔드포인트로 분리한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/members")
public class AdminMemberControllerImpl implements AdminMemberController {

    private final MemberAdminQueryService memberAdminQueryService;
    private final MemberAdminCommandService memberAdminCommandService;

    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminMemberResponse>>> list(
            @RequestParam(required = false) String keyword, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(memberAdminQueryService.search(keyword, pageable)));
    }

    @Override
    @GetMapping("/{memberId}")
    public ResponseEntity<ApiResponse<AdminMemberResponse>> detail(@PathVariable UUID memberId) {
        return ResponseEntity.ok(ApiResponse.ok(memberAdminQueryService.get(memberId)));
    }

    @Override
    @PostMapping("/{memberId}/suspend")
    public ResponseEntity<ApiResponse<AdminMemberResponse>> suspend(
            @LoginUser AuthUser admin, @PathVariable UUID memberId) {
        return ResponseEntity.ok(ApiResponse.ok(memberAdminCommandService.suspend(admin.id(), memberId)));
    }

    @Override
    @PostMapping("/{memberId}/unsuspend")
    public ResponseEntity<ApiResponse<AdminMemberResponse>> unsuspend(
            @LoginUser AuthUser admin, @PathVariable UUID memberId) {
        return ResponseEntity.ok(ApiResponse.ok(memberAdminCommandService.unsuspend(admin.id(), memberId)));
    }

    @Override
    @PatchMapping("/{memberId}/role")
    public ResponseEntity<ApiResponse<AdminMemberResponse>> changeRole(
            @LoginUser AuthUser admin, @PathVariable UUID memberId,
            @Valid @RequestBody RoleChangeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                memberAdminCommandService.changeRole(admin.id(), memberId, request.role())));
    }
}
