package com.glassvue.domain.catalog.controller;

import com.glassvue.domain.catalog.dto.ProductCreateRequest;
import com.glassvue.domain.catalog.dto.ProductResponse;
import com.glassvue.domain.catalog.dto.ProductSearchCondition;
import com.glassvue.domain.catalog.dto.ProductUpdateRequest;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

@Tag(name = "Product", description = "상품 API")
public interface ProductController {

    @Operation(summary = "상품 등록 (관리자)")
    ResponseEntity<ApiResponse<UUID>> create(@Valid ProductCreateRequest request);

    @Operation(summary = "상품 목록/검색 (이름·가격·상태·카테고리, 페이징)")
    ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> search(
            @ParameterObject ProductSearchCondition condition,
            @ParameterObject Pageable pageable);

    @Operation(summary = "상품 단건 조회")
    ResponseEntity<ApiResponse<ProductResponse>> get(UUID id);

    @Operation(summary = "상품 수정 (관리자)")
    ResponseEntity<ApiResponse<Void>> update(UUID id, @Valid ProductUpdateRequest request);

    @Operation(summary = "상품 삭제 (관리자)")
    ResponseEntity<ApiResponse<Void>> delete(UUID id);
}
