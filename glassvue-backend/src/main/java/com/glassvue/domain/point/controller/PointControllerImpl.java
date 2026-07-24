package com.glassvue.domain.point.controller;

import com.glassvue.domain.point.dto.PointAccountResponse;
import com.glassvue.domain.point.dto.PointHistoryResponse;
import com.glassvue.domain.point.service.PointService;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.response.PageResponse;
import com.glassvue.global.security.AuthUser;
import com.glassvue.global.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/points")
public class PointControllerImpl implements PointController {

    private final PointService pointService;

    @Override
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PointAccountResponse>> myAccount(@LoginUser AuthUser user) {
        return ResponseEntity.ok(ApiResponse.ok(pointService.myAccount(user.id())));
    }

    @Override
    @GetMapping("/me/history")
    public ResponseEntity<ApiResponse<PageResponse<PointHistoryResponse>>> myHistory(
            @LoginUser AuthUser user,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(pointService.myHistory(user.id(), pageable)));
    }
}
