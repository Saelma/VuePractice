package com.glassvue.domain.catalog.controller;

import com.glassvue.domain.catalog.dto.CategoryCreateRequest;
import com.glassvue.domain.catalog.dto.CategoryResponse;
import com.glassvue.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

@Tag(name = "Category", description = "카테고리 API")
public interface CategoryController {

    @Operation(summary = "카테고리 등록 (관리자)")
    ResponseEntity<ApiResponse<CategoryResponse>> create(@Valid CategoryCreateRequest request);

    @Operation(summary = "카테고리 목록")
    ResponseEntity<ApiResponse<List<CategoryResponse>>> list();

    @Operation(summary = "카테고리 삭제 (관리자) — 소속 상품이 없을 때만")
    ResponseEntity<ApiResponse<Void>> delete(UUID id);
}
