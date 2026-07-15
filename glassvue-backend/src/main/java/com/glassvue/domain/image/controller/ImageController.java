package com.glassvue.domain.image.controller;

import com.glassvue.domain.image.dto.ImageResponse;
import com.glassvue.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Image", description = "이미지 업로드 API (관리자)")
public interface ImageController {

    @Operation(summary = "이미지 업로드 → id·url 반환")
    ResponseEntity<ApiResponse<ImageResponse>> upload(MultipartFile file);
}
