package com.glassvue.domain.notification.controller;

import com.glassvue.domain.notification.dto.MarketingSendRequest;
import com.glassvue.domain.notification.dto.MarketingSendResponse;
import com.glassvue.domain.notification.service.MarketingCommandService;
import com.glassvue.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/notifications/marketing")
public class AdminMarketingControllerImpl implements AdminMarketingController {

    private final MarketingCommandService marketingService;

    @Override
    @GetMapping("/audience")
    public ResponseEntity<ApiResponse<Integer>> audience() {
        return ResponseEntity.ok(ApiResponse.ok(marketingService.audienceSize()));
    }

    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<MarketingSendResponse>> send(
            @Valid @RequestBody MarketingSendRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(marketingService.send(request)));
    }
}
