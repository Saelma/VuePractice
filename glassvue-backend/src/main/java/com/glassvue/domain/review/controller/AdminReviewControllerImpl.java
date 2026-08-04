package com.glassvue.domain.review.controller;

import com.glassvue.domain.review.dto.AdminReviewResponse;
import com.glassvue.domain.review.service.command.ReviewCommandService;
import com.glassvue.domain.review.service.query.ReviewQueryService;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.response.PageResponse;
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
@RequestMapping("/api/admin/reviews")
public class AdminReviewControllerImpl implements AdminReviewController {

    private final ReviewQueryService reviewQueryService;
    private final ReviewCommandService reviewCommandService;

    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminReviewResponse>>> list(
            // required = false 라 안 보내면 null = 전체다(true/false 와 구분되는 **세 번째 상태**).
            @RequestParam(required = false) Boolean hidden, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(reviewQueryService.findForAdmin(hidden, pageable)));
    }

    @Override
    @PostMapping("/{id}/hide")
    public ResponseEntity<ApiResponse<Void>> hide(@PathVariable UUID id) {
        reviewCommandService.setHidden(id, true);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Override
    @PostMapping("/{id}/unhide")
    public ResponseEntity<ApiResponse<Void>> unhide(@PathVariable UUID id) {
        reviewCommandService.setHidden(id, false);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
