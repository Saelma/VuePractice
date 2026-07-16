package com.glassvue.domain.inquiry.controller;

import com.glassvue.domain.inquiry.dto.InquiryAnswerRequest;
import com.glassvue.domain.inquiry.dto.InquiryCreateRequest;
import com.glassvue.domain.inquiry.dto.InquiryResponse;
import com.glassvue.domain.inquiry.dto.InquiryUpdateRequest;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.response.PageResponse;
import com.glassvue.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

@Tag(name = "Inquiry", description = "상품 문의 API (답변은 관리자만, 비밀글 지원)")
public interface InquiryController {

    @Operation(summary = "문의 작성 (로그인)")
    ResponseEntity<ApiResponse<UUID>> create(
            @Parameter(hidden = true) AuthUser user, UUID productId, InquiryCreateRequest request);

    @Operation(summary = "상품 문의 목록 (비밀글은 작성자·관리자 외 마스킹)")
    ResponseEntity<ApiResponse<PageResponse<InquiryResponse>>> list(
            @Parameter(hidden = true) AuthUser viewer, UUID productId, Pageable pageable);

    @Operation(summary = "문의 수정 (본인, 답변 전만)")
    ResponseEntity<ApiResponse<Void>> update(
            @Parameter(hidden = true) AuthUser user, UUID id, InquiryUpdateRequest request);

    @Operation(summary = "문의 삭제 (본인 또는 관리자)")
    ResponseEntity<ApiResponse<Void>> delete(@Parameter(hidden = true) AuthUser user, UUID id);

    @Operation(summary = "문의 답변 (관리자 전용)")
    ResponseEntity<ApiResponse<Void>> answer(UUID id, InquiryAnswerRequest request);
}
