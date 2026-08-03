package com.glassvue.domain.review.controller;

import com.glassvue.domain.review.dto.ProductReviewsResponse;
import com.glassvue.domain.review.dto.ReviewCreateRequest;
import com.glassvue.domain.review.dto.ReviewUpdateRequest;
import com.glassvue.domain.review.service.command.ReviewCommandService;
import com.glassvue.domain.review.service.query.ReviewQueryService;
import com.glassvue.global.response.ApiResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ReviewControllerImpl implements ReviewController {

    private final ReviewCommandService commandService;
    private final ReviewQueryService queryService;

    @Override
    @PostMapping("/products/{productId}/reviews")
    public ResponseEntity<ApiResponse<UUID>> create(
            @LoginUser AuthUser user,
            @PathVariable UUID productId,
            @Valid @RequestBody ReviewCreateRequest request) {
        UUID id = commandService.create(productId, request, user);
        return ResponseEntity.created(URI.create("/api/reviews/" + id)).body(ApiResponse.ok(id));
    }

    @Override
    @GetMapping("/products/{productId}/reviews")
    public ResponseEntity<ApiResponse<ProductReviewsResponse>> list(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "false") boolean photoOnly,
            Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.ok(queryService.getProductReviews(productId, photoOnly, pageable)));
    }

    @Override
    @PutMapping("/reviews/{id}")
    public ResponseEntity<ApiResponse<Void>> update(
            @LoginUser AuthUser user,
            @PathVariable UUID id,
            @Valid @RequestBody ReviewUpdateRequest request) {
        commandService.update(id, request, user);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Override
    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@LoginUser AuthUser user, @PathVariable UUID id) {
        commandService.delete(id, user);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
