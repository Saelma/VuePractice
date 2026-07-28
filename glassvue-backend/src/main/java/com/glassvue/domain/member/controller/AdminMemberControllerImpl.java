package com.glassvue.domain.member.controller;

import com.glassvue.domain.member.dto.AdminMemberResponse;
import com.glassvue.domain.member.service.query.MemberAdminQueryService;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.response.PageResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
