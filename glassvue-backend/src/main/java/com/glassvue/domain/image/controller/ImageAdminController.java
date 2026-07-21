package com.glassvue.domain.image.controller;

import com.glassvue.domain.image.service.ImageService;
import com.glassvue.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Image Admin", description = "이미지 운영 작업 (관리자)")
public interface ImageAdminController {

    @Operation(summary = "파생본 백필 — 파생본(medium·thumb)이 없는 기존 이미지에 생성해 채운다. 여러 번 실행해도 안전")
    ResponseEntity<ApiResponse<ImageService.BackfillResult>> backfillDerivatives();
}
