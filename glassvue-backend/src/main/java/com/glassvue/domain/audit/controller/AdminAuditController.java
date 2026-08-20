package com.glassvue.domain.audit.controller;

import com.glassvue.domain.audit.dto.AdminAuditLogResponse;
import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.domain.audit.entity.AuditTargetType;
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

    @Operation(summary = "감사 이력 조회 (회원·주문·상품·쿠폰·할인, 페이징)",
            description = """
                    누가(actor) 무엇을(target) 언제 어떻게 조작했는지의 append-only 이력.
                    정렬 미지정 시 최신순. action(조작 종류)·targetType(대상 종류)·targetLogin 으로 좁힐 수 있다.
                    targetType 은 대상이 회원이 아닌 행(상품·쿠폰)을 좁히는 유일한 수단이다 — targetLogin 이 비어 있어서다.
                    조회는 최상위 관리자(SUPER_ADMIN)만 — 조작 당사자가 자기 이력을 보는 구조를 막는다.""")
    ResponseEntity<ApiResponse<PageResponse<AdminAuditLogResponse>>> list(
            @Parameter(description = "조작 종류(비우면 전체)") AuditAction action,
            @Parameter(description = "대상 종류 MEMBER·PRODUCT·COUPON(비우면 전체)") AuditTargetType targetType,
            @Parameter(description = "대상 loginId 부분일치(비우면 전체). 대상이 회원인 행만 걸린다") String targetLogin,
            @ParameterObject Pageable pageable);
}
