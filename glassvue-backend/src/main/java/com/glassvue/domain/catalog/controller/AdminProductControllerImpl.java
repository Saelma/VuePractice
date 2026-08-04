package com.glassvue.domain.catalog.controller;

import com.glassvue.domain.catalog.dto.LowStockResponse;
import com.glassvue.domain.catalog.dto.StockHistoryResponse;
import com.glassvue.domain.catalog.service.query.ProductQueryService;
import com.glassvue.domain.catalog.service.query.StockHistoryQueryService;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/products")
public class AdminProductControllerImpl implements AdminProductController {

    private final ProductQueryService productQueryService;
    private final StockHistoryQueryService stockHistoryQueryService;

    @Override
    @GetMapping("/low-stock")
    public ResponseEntity<ApiResponse<LowStockResponse>> lowStock() {
        return ResponseEntity.ok(ApiResponse.ok(productQueryService.lowStock()));
    }

    @Override
    @GetMapping("/{id}/stock-history")
    public ResponseEntity<ApiResponse<PageResponse<StockHistoryResponse>>> stockHistory(
            @PathVariable UUID id, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(stockHistoryQueryService.byProduct(id, pageable)));
    }
}
