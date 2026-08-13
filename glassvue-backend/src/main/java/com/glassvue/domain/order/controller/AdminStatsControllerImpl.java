package com.glassvue.domain.order.controller;

import com.glassvue.domain.order.dto.SalesOverviewResponse;
import com.glassvue.domain.order.service.query.OrderStatsQueryService;
import com.glassvue.global.response.ApiResponse;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/stats")
public class AdminStatsControllerImpl implements AdminStatsController {

    private final OrderStatsQueryService statsQueryService;

    /**
     * ⚠ <b>{@code LocalDate} 로 받는다</b>(B-26). {@code Instant} 로 받으면 화면이 «그 날의 00:00 이
     * 언제인가» 를 계산하게 되고, 그러면 KST 경계가 <b>두 곳</b>에서 만들어진다.
     * 여기서 파싱만 하고 <b>경계는 서비스가 만든다.</b>
     */
    @Override
    @GetMapping("/sales")
    public ResponseEntity<ApiResponse<SalesOverviewResponse>> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok(statsQueryService.overview(from, to)));
    }
}
