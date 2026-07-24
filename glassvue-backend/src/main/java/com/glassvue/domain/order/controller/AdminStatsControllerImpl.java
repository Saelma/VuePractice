package com.glassvue.domain.order.controller;

import com.glassvue.domain.order.dto.SalesOverviewResponse;
import com.glassvue.domain.order.service.query.OrderStatsQueryService;
import com.glassvue.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/stats")
public class AdminStatsControllerImpl implements AdminStatsController {

    private final OrderStatsQueryService statsQueryService;

    @Override
    @GetMapping("/sales")
    public ResponseEntity<ApiResponse<SalesOverviewResponse>> overview() {
        return ResponseEntity.ok(ApiResponse.ok(statsQueryService.overview()));
    }
}
