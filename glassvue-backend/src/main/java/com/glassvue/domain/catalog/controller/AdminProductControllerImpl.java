package com.glassvue.domain.catalog.controller;

import com.glassvue.domain.catalog.dto.LowStockResponse;
import com.glassvue.domain.catalog.service.query.ProductQueryService;
import com.glassvue.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/products")
public class AdminProductControllerImpl implements AdminProductController {

    private final ProductQueryService productQueryService;

    @Override
    @GetMapping("/low-stock")
    public ResponseEntity<ApiResponse<LowStockResponse>> lowStock() {
        return ResponseEntity.ok(ApiResponse.ok(productQueryService.lowStock()));
    }
}
