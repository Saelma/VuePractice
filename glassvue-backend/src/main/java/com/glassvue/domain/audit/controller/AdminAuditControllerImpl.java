package com.glassvue.domain.audit.controller;

import com.glassvue.domain.audit.dto.AdminAuditLogResponse;
import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.domain.audit.entity.AuditTargetType;
import com.glassvue.domain.audit.service.query.AdminAuditQueryService;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 감사 이력 API. {@code /api/admin/audit/**} 는 SecurityConfig 에서 {@code /api/admin/**}(ADMIN) 위에
 * SUPER_ADMIN 규칙을 얹어 최상위만 열린다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/audit")
public class AdminAuditControllerImpl implements AdminAuditController {

    private final AdminAuditQueryService auditQueryService;

    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminAuditLogResponse>>> list(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) AuditTargetType targetType,
            @RequestParam(required = false) String targetLogin,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                auditQueryService.search(action, targetType, targetLogin, pageable)));
    }
}
