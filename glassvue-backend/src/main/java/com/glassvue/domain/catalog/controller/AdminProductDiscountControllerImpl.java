package com.glassvue.domain.catalog.controller;

import com.glassvue.domain.catalog.dto.ProductDiscountRequest;
import com.glassvue.domain.catalog.dto.ProductDiscountResponse;
import com.glassvue.domain.catalog.service.command.ProductDiscountCommandService;
import com.glassvue.domain.catalog.service.query.ProductQueryService;
import com.glassvue.global.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/products/{productId}/discounts")
public class AdminProductDiscountControllerImpl implements AdminProductDiscountController {

    private final ProductQueryService productQueryService;
    private final ProductDiscountCommandService discountCommandService;

    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductDiscountResponse>>> list(
            @PathVariable UUID productId) {
        return ResponseEntity.ok(ApiResponse.ok(productQueryService.discountsOf(productId)));
    }

    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<UUID>> create(
            @PathVariable UUID productId,
            @Valid @RequestBody ProductDiscountRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(discountCommandService.create(productId, request)));
    }

    @Override
    @PutMapping("/{discountId}")
    public ResponseEntity<ApiResponse<Void>> update(
            @PathVariable UUID productId,
            @PathVariable UUID discountId,
            @Valid @RequestBody ProductDiscountRequest request) {
        discountCommandService.update(productId, discountId, request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @Override
    @DeleteMapping("/{discountId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID productId,
            @PathVariable UUID discountId) {
        discountCommandService.delete(productId, discountId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
