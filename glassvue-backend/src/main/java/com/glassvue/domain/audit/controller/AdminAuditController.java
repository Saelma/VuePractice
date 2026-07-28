package com.glassvue.domain.audit.controller;

import com.glassvue.domain.audit.dto.AdminAuditLogResponse;
import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin Audit", description = "관리자 감사 이력 (SUPER_ADMIN 전용)")
public interface AdminAuditController {

    @Operation(summary = "감사 이력 조회 (정지·해제·역할변경, 페이징)",
            description = """
                    누가(actor) 누구를(target) 언제 어떻게 조작했는지의 append-only 이력.
                    정렬 미지정 시 최신순. action·targetLogin 으로 좁힐 수 있다.
                    조회는 최상위 관리자(SUPER_ADMIN)만 — 조작 당사자가 자기 이력을 보는 구조를 막는다.""")
    ResponseEntity<ApiResponse<PageResponse<AdminAuditLogResponse>>> list(
            @Parameter(description = "조작 종류(비우면 전체)") AuditAction action,
            @Parameter(description = "대상 loginId 부분일치(비우면 전체)") String targetLogin,
            @ParameterObject Pageable pageable);
}
