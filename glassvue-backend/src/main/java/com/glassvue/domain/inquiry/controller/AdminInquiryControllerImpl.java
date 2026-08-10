package com.glassvue.domain.inquiry.controller;

import com.glassvue.domain.inquiry.dto.AdminInquiryResponse;
import com.glassvue.domain.inquiry.entity.InquiryStatus;
import com.glassvue.domain.inquiry.service.query.InquiryQueryService;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.response.PageResponse;
import com.glassvue.global.security.AuthUser;
import com.glassvue.global.security.LoginUser;
import com.glassvue.domain.inquiry.service.command.InquiryCommandService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 권한은 경로가 건다 — {@code /api/admin/**} 가 ADMIN 을 요구한다(SecurityConfig, WA §2-4). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/inquiries")
public class AdminInquiryControllerImpl implements AdminInquiryController {

    private final InquiryQueryService inquiryQueryService;
    private final InquiryCommandService inquiryCommandService;

    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminInquiryResponse>>> list(
            // required = false 라 안 보내면 null = 전체다(WAITING/ANSWERED 와 구분되는 **세 번째 상태**).
            @RequestParam(required = false) InquiryStatus status,
            // hidden 도 같은 규약 — 안 보내면 숨김·노출을 **함께** 준다(관리자 리뷰와 같은 형태).
            @RequestParam(required = false) Boolean hidden, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(inquiryQueryService.findForAdmin(status, hidden, pageable)));
    }

    @Override
    @PostMapping("/{id}/hide")
    public ResponseEntity<ApiResponse<Void>> hide(@LoginUser AuthUser admin, @PathVariable UUID id) {
        inquiryCommandService.setHidden(id, true, admin);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Override
    @PostMapping("/{id}/unhide")
    public ResponseEntity<ApiResponse<Void>> unhide(@LoginUser AuthUser admin, @PathVariable UUID id) {
        inquiryCommandService.setHidden(id, false, admin);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
