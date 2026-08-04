package com.glassvue.domain.catalog.controller;

import com.glassvue.domain.catalog.dto.ProductCreateRequest;
import com.glassvue.domain.catalog.dto.ProductResponse;
import com.glassvue.domain.catalog.dto.ProductSearchCondition;
import com.glassvue.domain.catalog.dto.ProductUpdateRequest;
import com.glassvue.domain.catalog.service.command.ProductCommandService;
import com.glassvue.domain.catalog.service.query.ProductQueryService;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.response.PageResponse;
import com.glassvue.global.security.AuthUser;
import com.glassvue.global.security.LoginUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
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
@RequestMapping("/api/products")
public class ProductControllerImpl implements ProductController {

    private final ProductCommandService commandService;
    private final ProductQueryService queryService;

    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<UUID>> create(
            @LoginUser AuthUser user, @Valid @RequestBody ProductCreateRequest request) {
        UUID id = commandService.create(request, user);
        return ResponseEntity.created(URI.create("/api/products/" + id)).body(ApiResponse.ok(id));
    }

    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> search(
            ProductSearchCondition condition, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.search(condition, pageable)));
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.get(id)));
    }

    @Override
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> update(@LoginUser AuthUser user,
            @PathVariable UUID id, @Valid @RequestBody ProductUpdateRequest request) {
        commandService.update(id, request, user);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Override
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        commandService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
