package com.glassvue.domain.audit.controller;

import com.glassvue.domain.audit.dto.AdminAuditLogResponse;
import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.domain.audit.entity.AuditTargetType;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin Audit", description = "관리자 감사 이력 (SUPER_ADMIN 전용)")
public interface AdminAuditController {

    @Operation(summary = "감사 이력 조회 (회원·주문·상품·쿠폰·할인, 페이징)",
            description = """
                    누가(actor) 무엇을(target) 언제 어떻게 조작했는지의 append-only 이력.
                    정렬 미지정 시 최신순. actions(조작 종류, 여러 개 — 쉼표로)·targetType(대상 종류)·targetLogin 으로 좁힐 수 있다.
                    targetType 은 대상이 회원이 아닌 행(상품·쿠폰)을 좁히는 유일한 수단이다 — targetLogin 이 비어 있어서다.
                    조회는 최상위 관리자(SUPER_ADMIN)만 — 조작 당사자가 자기 이력을 보는 구조를 막는다.
                    from·to 로 기간을 좁힌다(B-26) — 「그날 누가 무엇을 했나」가 감사 로그의 존재 이유에 가까운 질문이다.""")
    ResponseEntity<ApiResponse<PageResponse<AdminAuditLogResponse>>> list(
            @Parameter(description = "조작 종류 — 여러 개(`actions=REVIEW_DELETE,INQUIRY_DELETE`). 비우면 전체")
            List<AuditAction> actions,
            @Parameter(description = "대상 종류 MEMBER·PRODUCT·COUPON(비우면 전체)") AuditTargetType targetType,
            @Parameter(description = "대상 loginId 부분일치(비우면 전체). 대상이 회원인 행만 걸린다") String targetLogin,
            @Parameter(description = "기간 시작 yyyy-MM-dd(그 날 포함). 날짜만 받는다 — 경계는 서버가 만든다")
            LocalDate from,
            @Parameter(description = "기간 종료 yyyy-MM-dd(그 날 포함)") LocalDate to,
            @ParameterObject Pageable pageable);
}
