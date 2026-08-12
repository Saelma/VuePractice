package com.glassvue.domain.catalog.controller;

import com.glassvue.domain.catalog.dto.DeletedProductResponse;
import com.glassvue.domain.catalog.dto.LowStockResponse;
import com.glassvue.domain.catalog.dto.StockHistoryResponse;
import com.glassvue.domain.catalog.service.command.ProductCommandService;
import com.glassvue.domain.catalog.service.query.ProductQueryService;
import com.glassvue.domain.catalog.service.query.StockHistoryQueryService;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.response.PageResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/products")
public class AdminProductControllerImpl implements AdminProductController {

    private final ProductQueryService productQueryService;
    private final StockHistoryQueryService stockHistoryQueryService;
    private final ProductCommandService productCommandService;

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

    // ── 삭제 유예 (2026-08-12, F-7) ──────────────────────────────
    //
    // ⚠ 경로가 `/deleted` 다. `/{id}/...` 가 위에 있지만 **구체 경로가 우선**이라 충돌하지 않는다
    //    (스프링은 변수 없는 패턴을 먼저 고른다). 그래도 «상품 id 가 deleted 일 리 없다» 에 기대는
    //    모양이라, 새 하위 경로를 더할 때는 이 순서를 한 번 확인하고 넣는다.

    @Override
    @GetMapping("/deleted")
    public ResponseEntity<ApiResponse<List<DeletedProductResponse>>> deleted() {
        // 페이징을 안 붙였다 — 유예가 7일이라 목록이 길어질 구조가 아니다(길어지면 그때 붙인다).
        return ResponseEntity.ok(ApiResponse.ok(productQueryService.findDeleted()));
    }

    @Override
    @PostMapping("/{id}/restore")
    public ResponseEntity<ApiResponse<Void>> restore(@PathVariable UUID id) {
        productCommandService.restore(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
