package com.glassvue.domain.member.controller;

import com.glassvue.domain.member.dto.AdminMemberResponse;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
}
