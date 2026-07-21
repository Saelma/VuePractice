package com.glassvue.domain.image.controller;

import com.glassvue.domain.image.service.ImageService;
import com.glassvue.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 경로를 {@code /api/admin/**} 아래 두어 SecurityConfig의 한 줄(hasRole ADMIN)로 막힌다
 * — 관리 엔드포인트가 늘어도 개별 매처를 빠뜨릴 수 없다(WORKING-AGREEMENTS §2-4).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/images")
public class ImageAdminControllerImpl implements ImageAdminController {

    private final ImageService imageService;

    @Override
    @PostMapping("/derivatives")
    public ResponseEntity<ApiResponse<ImageService.BackfillResult>> backfillDerivatives() {
        return ResponseEntity.ok(ApiResponse.ok(imageService.backfillDerivatives()));
    }
}
