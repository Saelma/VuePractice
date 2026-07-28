package com.glassvue.domain.point.controller;

import com.glassvue.domain.point.dto.PointAccountResponse;
import com.glassvue.domain.point.dto.PointHistoryResponse;
import com.glassvue.domain.point.service.PointService;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.response.PageResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 적립금 API. {@code /api/admin/**} 한 줄(SecurityConfig)로 ADMIN 보호된다.
 * 사용자용 {@code PointControllerImpl}(/api/points, 본인)과 경로·권한을 분리한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/points")
public class AdminPointControllerImpl implements AdminPointController {

    private final PointService pointService;

    @Override
    @GetMapping("/{memberId}/account")
    public ResponseEntity<ApiResponse<PointAccountResponse>> account(@PathVariable UUID memberId) {
        return ResponseEntity.ok(ApiResponse.ok(pointService.accountOf(memberId)));
    }

    @Override
    @GetMapping("/{memberId}/history")
    public ResponseEntity<ApiResponse<PageResponse<PointHistoryResponse>>> history(
            @PathVariable UUID memberId, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(pointService.historyOf(memberId, pageable)));
    }
}
